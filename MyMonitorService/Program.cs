using System.Net.Http;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using MyMonitorService.Core.Config;
using MyMonitorService.Core.Diagnostics;
using MyMonitorService.Core.Network;
using MyMonitorService.Features.Audio;
using MyMonitorService.Features.RemoteCommands;
using MyMonitorService.Features.ScreenCapture;
using MyMonitorService.Features.ServiceLifecycle;

namespace MyMonitorService;

public class Program
{
    public static void Main(string[] args)
    {
        var builder = Host.CreateApplicationBuilder(args);
        
        // تكوين التطبيق ليعمل كخدمة ويندوز (Windows Service)
        builder.Services.AddWindowsService(options =>
        {
            options.ServiceName = "MyMonitorService";
        });

        // 1. تسجيل الإعدادات والنواة المشتركة (Core)
        builder.Services.AddSingleton(AppConfig.Default);
        builder.Services.AddSingleton<HttpClient>();
        builder.Services.AddSingleton<SupabaseRestClient>();
        builder.Services.AddSingleton<SupabaseStorageClient>();
        builder.Services.AddSingleton<ISystemMetricsProvider, WmiSystemMetricsProvider>();

        // 2. تسجيل ميزات الصوت (Features/Audio)
        builder.Services.AddSingleton<NaudioWaveRecorder>();
        builder.Services.AddSingleton<WasapiLoopbackRecorder>();

        // 3. تسجيل ميزات التقاط الشاشة (Features/ScreenCapture)
        builder.Services.AddSingleton<IScreenCaptureEngine, GdiScreenCaptureEngine>();
        builder.Services.AddSingleton<ScreenCaptureService>();

        // 4. تسجيل ميزات الأوامر عن بُعد (Features/RemoteCommands)
        builder.Services.AddSingleton<ICommandExecutor, CommandExecutor>();
        builder.Services.AddSingleton<CommandPollingService>();

        // 5. تسجيل السجل المحلي ومنسق الخلفية (Features/ServiceLifecycle)
        builder.Services.AddSingleton<LocalFileLogger>();
        builder.Services.AddHostedService<MonitoringWorker>();

        var host = builder.Build();
        host.Run();
    }
}
