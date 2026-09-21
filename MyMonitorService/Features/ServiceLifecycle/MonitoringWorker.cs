using System;
using System.Runtime.Versioning;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using MyMonitorService.Core.Config;
using MyMonitorService.Core.Diagnostics;
using MyMonitorService.Core.Network;
using MyMonitorService.Features.RemoteCommands;

namespace MyMonitorService.Features.ServiceLifecycle;

/// <summary>
/// منسق الخدمة الخفيف (Thin BackgroundService) وفق معمارية CCP
/// </summary>
[SupportedOSPlatform("windows")]
public class MonitoringWorker : BackgroundService
{
    private readonly AppConfig _config;
    private readonly ISystemMetricsProvider _metricsProvider;
    private readonly SupabaseRestClient _restClient;
    private readonly CommandPollingService _commandPoller;
    private readonly LocalFileLogger _localLogger;
    private readonly ILogger<MonitoringWorker> _logger;

    public MonitoringWorker(
        AppConfig config,
        ISystemMetricsProvider metricsProvider,
        SupabaseRestClient restClient,
        CommandPollingService commandPoller,
        LocalFileLogger localLogger,
        ILogger<MonitoringWorker> logger)
    {
        _config = config;
        _metricsProvider = metricsProvider;
        _restClient = restClient;
        _commandPoller = commandPoller;
        _localLogger = localLogger;
        _logger = logger;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        _logger.LogInformation("🚀 بدأت خدمة MyMonitorService المعمارية (CCP) بنجاح في: {time}", DateTimeOffset.Now);

        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                // 1. تجميع المقاييس وبناء التقرير
                string deviceInfo = _metricsProvider.BuildFormattedSystemInfo();
                string timestamp = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");

                var localReport = new StringBuilder();
                localReport.AppendLine("==================================================");
                localReport.AppendLine($"تاريخ ووقت التقرير: {timestamp}");
                localReport.AppendLine($"معلومات النظام:     {deviceInfo}");
                localReport.AppendLine("==================================================");
                localReport.AppendLine();

                // 2. الحفظ المحلي
                await _localLogger.WriteDailyReportAsync(localReport.ToString(), stoppingToken);

                // 3. المزامنة السحابية مع Supabase (بالمعرف المعتمد)
                await _restClient.SyncDeviceSettingsAsync(
                    DeviceIdentity.DeviceId,
                    DeviceIdentity.DisplayName,
                    deviceInfo,
                    stoppingToken);

                // 4. فحص وتنفيذ الأوامر عن بُعد
                await _commandPoller.PollAndExecuteAsync(stoppingToken);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "حدث استثناء أثناء دورة عمل MonitoringWorker.");
            }

            // الانتظار للدورة القادمة
            await Task.Delay(_config.PollInterval, stoppingToken);
        }

        _logger.LogInformation("🛑 تم إيقاف خدمة MyMonitorService في: {time}", DateTimeOffset.Now);
    }
}
