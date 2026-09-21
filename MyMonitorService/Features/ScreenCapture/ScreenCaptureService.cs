using System;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.Extensions.Logging;
using MyMonitorService.Core.Network;

namespace MyMonitorService.Features.ScreenCapture;

public class ScreenCaptureService
{
    private readonly IScreenCaptureEngine _captureEngine;
    private readonly SupabaseStorageClient _storageClient;
    private readonly ILogger<ScreenCaptureService> _logger;

    public ScreenCaptureService(
        IScreenCaptureEngine captureEngine,
        SupabaseStorageClient storageClient,
        ILogger<ScreenCaptureService> logger)
    {
        _captureEngine = captureEngine;
        _storageClient = storageClient;
        _logger = logger;
    }

    /// <summary>
    /// التقاط صورة الشاشة ورفعها مباشرة لمجلد screenshots/{deviceId}/ في Supabase Storage
    /// </summary>
    public async Task<bool> CaptureAndUploadAsync(string deviceId, CancellationToken ct = default)
    {
        try
        {
            byte[]? jpegData = await _captureEngine.CaptureScreenJpegAsync(ct);
            if (jpegData == null || jpegData.Length == 0)
            {
                _logger.LogWarning("لم يتم الحصول على بيانات صورة صالحة لالتقاط الشاشة.");
                return false;
            }

            string timestamp = DateTime.Now.ToString("yyyyMMdd_HHmmss");
            string remotePath = $"screenshots/{deviceId}/shot_{timestamp}.jpg";

            _logger.LogInformation("⏫ جاري رفع لقطة الشاشة لـ Supabase: {path}", remotePath);
            return await _storageClient.UploadBinaryAsync(remotePath, jpegData, "image/jpeg", ct);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "فشل التقاط أو رفع لقطة الشاشة.");
            return false;
        }
    }
}
