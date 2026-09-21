using System;
using System.IO;

namespace MyMonitorService.Core.Config;

/// <summary>
/// إعدادات الاتصال المركزية والثوابت الفنية لخدمة مراقبة ويندوز
/// </summary>
public class AppConfig
{
    // قراءة بيانات الاتصال ديناميكياً من البيئة أو ملف التكوين لمنع إدراج مفاتيح صريحة
    public string SupabaseBaseUrl { get; set; } = string.Empty;
    public string AnonKey { get; set; } = string.Empty;
    public string StorageBucket { get; set; } = "monitoring_data";

    // المسارات المحلية
    public string LogDirectory { get; set; } = @"C:\ProgramData\MyMonitor\logs\";

    // الفترات الزمنية
    public TimeSpan PollInterval { get; set; } = TimeSpan.FromSeconds(30);
    public TimeSpan HeartbeatInterval { get; set; } = TimeSpan.FromSeconds(60);

    // عناوين الـ REST endpoints المحسوبة
    public string RestRemoteSettingsUrl => $"{SupabaseBaseUrl.TrimEnd('/')}/rest/v1/remote_settings?on_conflict=device_id";
    public string RestCommandsUrl => $"{SupabaseBaseUrl.TrimEnd('/')}/rest/v1/commands";
    public string RestLogsUrl => $"{SupabaseBaseUrl.TrimEnd('/')}/rest/v1/remote_logs";
    public string StorageObjectUrl => $"{SupabaseBaseUrl.TrimEnd('/')}/storage/v1/object/{StorageBucket}";

    public static AppConfig Default { get; } = LoadFromEnvironment();

    public static AppConfig LoadFromEnvironment()
    {
        var config = new AppConfig();

        // 1. فحص متغيرات البيئة
        string? envUrl = Environment.GetEnvironmentVariable("SUPABASE_URL");
        string? envKey = Environment.GetEnvironmentVariable("SUPABASE_KEY") ?? 
                         Environment.GetEnvironmentVariable("SUPABASE_ANON_KEY");

        // 2. فحص ملف .env في مجلد التطبيق أو المجلدات الأب إن وجد
        if (string.IsNullOrEmpty(envUrl) || string.IsNullOrEmpty(envKey))
        {
            TryLoadFromDotEnv(ref envUrl, ref envKey);
        }

        config.SupabaseBaseUrl = envUrl ?? "https://ahiqnmheiqigrdbishwe.supabase.co";
        config.AnonKey = envKey ?? string.Empty;

        return config;
    }

    private static void TryLoadFromDotEnv(ref string? url, ref string? key)
    {
        try
        {
            string currentDir = AppDomain.CurrentDomain.BaseDirectory;
            var dir = new DirectoryInfo(currentDir);
            for (int i = 0; i < 4 && dir != null; i++)
            {
                string envPath = Path.Combine(dir.FullName, ".env");
                if (File.Exists(envPath))
                {
                    foreach (string line in File.ReadAllLines(envPath))
                    {
                        string trimmed = line.Trim();
                        if (trimmed.StartsWith('#') || !trimmed.Contains('=')) continue;
                        int idx = trimmed.IndexOf('=');
                        string k = trimmed[..idx].Trim();
                        string v = trimmed[(idx + 1)..].Trim().Trim('"', '\'');

                        if (string.IsNullOrEmpty(url) && k.Equals("SUPABASE_URL", StringComparison.OrdinalIgnoreCase))
                            url = v;
                        else if (string.IsNullOrEmpty(key) && (k.Equals("SUPABASE_KEY", StringComparison.OrdinalIgnoreCase) || k.Equals("SUPABASE_ANON_KEY", StringComparison.OrdinalIgnoreCase)))
                            key = v;
                    }
                    break;
                }
                dir = dir.Parent;
            }
        }
        catch { }
    }
}
