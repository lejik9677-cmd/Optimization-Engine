using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Management;
using System.Runtime.InteropServices;
using System.Runtime.Versioning;
using Microsoft.Extensions.Logging;

namespace MyMonitorService.Core.Diagnostics;

[SupportedOSPlatform("windows")]
public class WmiSystemMetricsProvider : ISystemMetricsProvider
{
    private readonly ILogger<WmiSystemMetricsProvider> _logger;

    public string MachineName => Environment.MachineName;
    public string UserName => GetActiveUserName();
    public string OsVersion => RuntimeInformation.OSDescription;

    private static readonly HashSet<string> IgnoredProcesses = new(StringComparer.OrdinalIgnoreCase)
    {
        "system", "idle", "svchost", "csrss", "winlogon", "lsass", "services",
        "smss", "dwmer", "ctfmon", "fontdrvhost", "sihost", "taskhostw",
        "SearchHost", "StartMenuExperienceHost", "RuntimeBroker", "ShellExperienceHost",
        "SearchIndexer", "SecurityHealthService", "smartscreen", "conhost",
        "wlanext", "spoolsv", "SearchFilterHost", "SearchProtocolHost", "MyMonitorService"
    };

    public WmiSystemMetricsProvider(ILogger<WmiSystemMetricsProvider> logger)
    {
        _logger = logger;
    }

    public double GetCpuUsage()
    {
        try
        {
            using var searcher = new ManagementObjectSearcher(
                "SELECT PercentProcessorTime FROM Win32_PerfFormattedData_PerfOS_Processor WHERE Name='_Total'");
            foreach (ManagementObject obj in searcher.Get())
            {
                var val = obj["PercentProcessorTime"];
                if (val != null) return Convert.ToDouble(val);
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "فشل جلب استهلاك المعالج عبر WMI.");
        }
        return 0;
    }

    public (double ramPercent, double usedGb, double totalGb) GetRamUsage()
    {
        try
        {
            using var searcher = new ManagementObjectSearcher(
                "SELECT TotalVisibleMemorySize, FreePhysicalMemory FROM Win32_OperatingSystem");
            foreach (ManagementObject obj in searcher.Get())
            {
                var totalVal = obj["TotalVisibleMemorySize"];
                var freeVal = obj["FreePhysicalMemory"];
                if (totalVal != null && freeVal != null)
                {
                    double totalMemoryKb = Convert.ToDouble(totalVal);
                    double freeMemoryKb = Convert.ToDouble(freeVal);

                    double totalGb = totalMemoryKb / (1024.0 * 1024.0);
                    double freeGb = freeMemoryKb / (1024.0 * 1024.0);
                    double usedGb = totalGb - freeGb;
                    double ramPercent = totalGb > 0 ? (usedGb / totalGb) * 100.0 : 0;

                    return (ramPercent, usedGb, totalGb);
                }
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "فشل جلب معلومات الذاكرة عبر WMI.");
        }
        return (0, 0, 0);
    }

    public string GetActiveUserName()
    {
        try
        {
            using var searcher = new ManagementObjectSearcher("SELECT UserName FROM Win32_ComputerSystem");
            foreach (ManagementObject obj in searcher.Get())
            {
                string val = obj["UserName"]?.ToString() ?? "";
                if (!string.IsNullOrWhiteSpace(val))
                {
                    int slashIndex = val.IndexOf('\\');
                    if (slashIndex >= 0 && slashIndex < val.Length - 1)
                    {
                        return val.Substring(slashIndex + 1);
                    }
                    return val;
                }
            }
        }
        catch { }

        string envUser = Environment.UserName;
        if (string.Equals(envUser, "SYSTEM", StringComparison.OrdinalIgnoreCase))
        {
            return "Desktop User";
        }
        return envUser;
    }

    public List<string> GetOpenApplications()
    {
        var appNames = new List<string>();
        try
        {
            var processes = Process.GetProcesses();
            foreach (var process in processes)
            {
                try
                {
                    if ((process.SessionId > 0 || process.MainWindowHandle != IntPtr.Zero) &&
                        !IgnoredProcesses.Contains(process.ProcessName))
                    {
                        appNames.Add(process.ProcessName);
                    }
                }
                catch
                {
                    // تخطي العمليات المحمية
                }
                finally
                {
                    process.Dispose();
                }
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "حدث خطأ أثناء فحص العمليات النشطة.");
        }

        return appNames.Distinct().OrderBy(name => name).ToList();
    }

    public string BuildFormattedSystemInfo()
    {
        double cpu = GetCpuUsage();
        var (ramPercent, usedRam, totalRam) = GetRamUsage();
        var apps = GetOpenApplications();
        string appsStr = apps.Count > 0 ? string.Join(", ", apps) : "لا توجد تطبيقات مفتوحة";

        return $"{MachineName} (OS: {OsVersion} | CPU: {cpu:F1}% | RAM: {ramPercent:F1}% ({usedRam:F1}/{totalRam:F1} GB) | User: {UserName} | Open Apps: {appsStr})";
    }
}
