using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;

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

        // تسجيل خدمة الخلفية (Background Worker) التي تقوم بجمع البيانات
        builder.Services.AddHostedService<Worker>();

        var host = builder.Build();
        host.Run();
    }
}
