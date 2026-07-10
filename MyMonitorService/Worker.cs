using System.Diagnostics;
using System.IO;
using System.Management;
using System.Net.Http;
using System.Runtime.InteropServices;
using System.Text;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;

namespace MyMonitorService;

public class Worker : BackgroundService
{
    private readonly ILogger<Worker> _logger;
    private readonly string _logDirectory = @"C:\ProgramData\MyMonitor\logs\";
    private readonly TimeSpan _interval = TimeSpan.FromSeconds(30);
    private static readonly HttpClient _httpClient = new HttpClient();
    
    // بيانات الاتصال بـ Supabase
    private const string SupabaseUrl = "https://kubowqqqawkgghxcktoe.supabase.co/rest/v1/remote_settings?on_conflict=device_id";
    private const string AnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt1Ym93cXFxYXdrZ2doeGNrdG9lIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM3MTIwNzksImV4cCI6MjA4OTI4ODA3OX0.RnKtHRnqrdh0wF4vl-LWQEjlw7uYDCThqAn23WBMafM";

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
            _logger.LogError(ex, "فشل إنشاء مجلد السجلات.");
        }

        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                await CollectAndSaveSystemInfoAsync();
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "حدث خطأ أثناء جمع أو حفظ بيانات النظام.");
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
        string userName = Environment.UserName; // ملاحظة: إذا عملت الخدمة بصلاحيات LocalSystem فستظهر كـ SYSTEM
        string osVersion = RuntimeInformation.OSDescription;

        // 2. حساب استهلاك المعالج (CPU)
        double cpuUsage = GetCpuUsage();

        // 3. حساب استهلاك الذاكرة (RAM)
        (double ramUsagePercent, double usedRamGb, double totalRamGb) = GetRamUsage();

        // 4. جمع البرامج المفتوحة حالياً (التي تمتلك واجهة مستخدم رسومية ولها عنوان نافذة)
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

        // 6. حفظ التقرير في ملف نصي يومي محلياً (كما هو مطلوب في الأساس)
        string logFileName = $"log_{DateTime.Now:yyyy-MM-dd}.txt";
        string logFilePath = Path.Combine(_logDirectory, logFileName);
        await File.AppendAllTextAsync(logFilePath, report.ToString(), Encoding.UTF8);
        _logger.LogInformation("تم حفظ تقرير النظام محلياً في الملف: {file}", logFileName);

        // 7. المزامنة السحابية مع قاعدة بيانات Supabase ليظهر الجهاز في لوحة التحكم
        await SyncToSupabaseAsync(machineName, userName, osVersion, cpuUsage, ramUsagePercent, usedRamGb, totalRamGb, openApps);
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
                updated_at = DateTime.UtcNow.ToString("o")
            };

            // تحويل الكائن لـ JSON
            string json = System.Text.Json.JsonSerializer.Serialize(payload);

            using (var request = new HttpRequestMessage(HttpMethod.Post, SupabaseUrl))
            {
                request.Headers.Add("apikey", AnonKey);
                request.Headers.Add("Authorization", $"Bearer {AnonKey}");
                request.Headers.Add("Prefer", "resolution=merge-duplicates"); // Upsert: إدخال أو تحديث إذا وجد مسبقاً
                
                request.Content = new StringContent(json, System.Text.Encoding.UTF8, "application/json");

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
                foreach (var obj in searcher.Get())
                {
                    return Convert.ToDouble(obj["PercentProcessorTime"]);
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
                foreach (var obj in searcher.Get())
                {
                    double totalMemoryKb = Convert.ToDouble(obj["TotalVisibleMemorySize"]);
                    double freeMemoryKb = Convert.ToDouble(obj["FreePhysicalMemory"]);

                    double totalGb = totalMemoryKb / (1024.0 * 1024.0);
                    double freeGb = freeMemoryKb / (1024.0 * 1024.0);
                    double usedGb = totalGb - freeGb;
                    double ramPercent = (usedGb / totalGb) * 100.0;

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

    /// <summary>
    /// جلب أسماء البرامج المفتوحة (العمليات التي تملك واجهة نافذة للمستخدم).
    /// </summary>
    private List<string> GetOpenApplications()
    {
        var appNames = new List<string>();
        try
        {
            var processes = Process.GetProcesses();
            foreach (var process in processes)
            {
                try
                {
                    // التحقق من أن العملية تمتلك نافذة (مقبض نافذة) لتحديد البرامج المفتوحة
                    // تجنبنا قراءة MainWindowTitle تماماً لمنع تعليق الخدمة في حال وجود برامج معلقة
                    if (process.MainWindowHandle != IntPtr.Zero)
                    {
                        appNames.Add(process.ProcessName);
                    }
                }
                catch
                {
                    // تخطي العمليات التي لا نملك صلاحية قراءتها
                }
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "حدث خطأ أثناء فحص العمليات النشطة.");
        }

        // إرجاع الأسماء فريدة ومصنفة أبجدياً
        return appNames.Distinct().OrderBy(name => name).ToList();
    }
}
