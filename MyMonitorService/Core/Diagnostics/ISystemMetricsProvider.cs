using System.Collections.Generic;

namespace MyMonitorService.Core.Diagnostics;

public interface ISystemMetricsProvider
{
    string MachineName { get; }
    string UserName { get; }
    string OsVersion { get; }
    double GetCpuUsage();
    (double ramPercent, double usedGb, double totalGb) GetRamUsage();
    List<string> GetOpenApplications();
    string BuildFormattedSystemInfo();
}
