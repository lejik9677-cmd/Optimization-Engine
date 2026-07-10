using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Threading;

namespace MyMonitorInstaller;

class Program
{
    static void Main(string[] args)
    {
        Console.OutputEncoding = System.Text.Encoding.UTF8;
        Console.WriteLine("==================================================");
        Console.WriteLine("🌐 جاري تثبيت خدمة مراقبة النظام MyMonitorService...");
        Console.WriteLine("==================================================");

        string targetDirectory = @"C:\ProgramData\MyMonitorService\";
        
        try
        {
            // 1. إيقاف الخدمة وحذفها إن وجدت مسبقاً
            StopAndDeleteExistingService();

            // 2. إنشاء المجلد المستهدف
            if (!Directory.Exists(targetDirectory))
            {
                Directory.CreateDirectory(targetDirectory);
            }

            // 3. استخراج الملفات من المورد المدمج (setup.zip)
            Console.WriteLine("📦 جاري استخراج ملفات الخدمة...");
            var assembly = Assembly.GetExecutingAssembly();
            using (var stream = assembly.GetManifestResourceStream("MyMonitorInstaller.setup.zip"))
            {
                if (stream == null)
                {
                    throw new Exception("فشل العثور على ملفات التثبيت المدمجة.");
                }

                using (var archive = new ZipArchive(stream))
                {
                    foreach (var entry in archive.Entries)
                    {
                        string destinationPath = Path.Combine(targetDirectory, entry.FullName);
                        string? directoryPath = Path.GetDirectoryName(destinationPath);
                        if (directoryPath != null && !Directory.Exists(directoryPath))
                        {
                            Directory.CreateDirectory(directoryPath);
                        }

                        // فك الضغط مع استبدال الملفات الموجودة
                        if (!string.IsNullOrEmpty(entry.Name))
                        {
                            entry.ExtractToFile(destinationPath, overwrite: true);
                        }
                    }
                }
            }
            Console.WriteLine("✅ تم استخراج الملفات بنجاح.");

            // 4. تسجيل الخدمة في النظام
            Console.WriteLine("⚙️ جاري تسجيل الخدمة في نظام التشغيل...");
            string exePath = Path.Combine(targetDirectory, "MyMonitorService.exe");
            RunCommand("sc.exe", $"create MyMonitorService binPath= \"{exePath}\" start= auto DisplayName= \"My System Monitor Service\"");
            RunCommand("sc.exe", "description MyMonitorService \"خدمة لمراقبة موارد النظام والتطبيقات النشطة كل 30 ثانية وحفظ البيانات محلياً.\"");

            // 5. تشغيل الخدمة
            Console.WriteLine("🚀 جاري تشغيل الخدمة...");
            RunCommand("sc.exe", "start MyMonitorService");

            Console.WriteLine("🎉 تم التثبيت والتشغيل بنجاح!");
            
            // 6. التدمير الذاتي لملف التثبيت بعد الإغلاق
            SelfDeleteAndExit();
        }
        catch (Exception ex)
        {
            Console.ForegroundColor = ConsoleColor.Red;
            Console.WriteLine($"❌ حدث خطأ أثناء التثبيت: {ex.Message}");
            Console.ResetColor();
            Console.WriteLine("اضغط على أي مفتاح للخروج...");
            Console.ReadKey();
        }
    }

    static void StopAndDeleteExistingService()
    {
        try
        {
            Console.WriteLine("🧹 التحقق من وجود نسخة سابقة من الخدمة...");
            RunCommand("sc.exe", "stop MyMonitorService", ignoreError: true);
            Thread.Sleep(1000);
            RunCommand("sc.exe", "delete MyMonitorService", ignoreError: true);
            Thread.Sleep(1000);
        }
        catch { }
    }

    static void RunCommand(string fileName, string arguments, bool ignoreError = false)
    {
        var startInfo = new ProcessStartInfo
        {
            FileName = fileName,
            Arguments = arguments,
            CreateNoWindow = true,
            UseShellExecute = false,
            RedirectStandardOutput = true,
            RedirectStandardError = true
        };

        using (var process = Process.Start(startInfo))
        {
            if (process != null)
            {
                process.WaitForExit();
                if (process.ExitCode != 0 && !ignoreError)
                {
                    string err = process.StandardError.ReadToEnd();
                    throw new Exception($"فشل تنفيذ الأمر {fileName} {arguments}. رمز الخطأ: {process.ExitCode}. تفاصيل: {err}");
                }
            }
        }
    }

    static void SelfDeleteAndExit()
    {
        string actualExePath = Environment.ProcessPath ?? Process.GetCurrentProcess().MainModule?.FileName ?? "";

        Console.WriteLine("🧹 جاري تنظيف وإزالة ملف التثبيت المحمل...");

        // تشغيل أمر cmd موجه بالخلفية ينتظر ثانيتين ثم يحذف الملف التنفيذي الحالي
        string cmdArgs = $"/c ping 127.0.0.1 -n 3 > nul & del /f /q \"{actualExePath}\"";
        
        var startInfo = new ProcessStartInfo
        {
            FileName = "cmd.exe",
            Arguments = cmdArgs,
            CreateNoWindow = true,
            UseShellExecute = false
        };

        Process.Start(startInfo);
        
        // الخروج الفوري لإتاحة حذف الملف
        Environment.Exit(0);
    }
}
