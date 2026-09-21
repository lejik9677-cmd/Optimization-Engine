using System;
using System.IO;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.Extensions.Logging;
using MyMonitorService.Core.Config;

namespace MyMonitorService.Features.ServiceLifecycle;

public class LocalFileLogger
{
    private readonly AppConfig _config;
    private readonly ILogger<LocalFileLogger> _logger;

    public LocalFileLogger(AppConfig config, ILogger<LocalFileLogger> logger)
    {
        _config = config;
        _logger = logger;
        EnsureDirectoryExists();
    }

    private void EnsureDirectoryExists()
    {
        try
        {
            if (!Directory.Exists(_config.LogDirectory))
            {
                Directory.CreateDirectory(_config.LogDirectory);
                _logger.LogInformation("تم إنشاء مجلد السجلات في: {path}", _config.LogDirectory);
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "فشل إنشاء مجلد السجلات في: {path}", _config.LogDirectory);
        }
    }

    public async Task WriteDailyReportAsync(string content, CancellationToken ct = default)
    {
        try
        {
            EnsureDirectoryExists();
            string fileName = $"log_{DateTime.Now:yyyy-MM-dd}.txt";
            string filePath = Path.Combine(_config.LogDirectory, fileName);
            await File.AppendAllTextAsync(filePath, content, Encoding.UTF8, ct);
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "فشل كتابة التقرير في ملف السجل المحلي.");
        }
    }
}
