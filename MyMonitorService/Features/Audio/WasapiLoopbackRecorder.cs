using System;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.Extensions.Logging;
using NAudio.Wave;

namespace MyMonitorService.Features.Audio;

/// <summary>
/// تسجيل صوت النظام المكتبي (Desktop Audio Loopback) لسماع المكالمات والوسائط المشغلة عبر السماعات
/// </summary>
public class WasapiLoopbackRecorder : IAudioRecorder
{
    private readonly ILogger<WasapiLoopbackRecorder> _logger;
    private readonly SemaphoreSlim _lock = new(1, 1);

    public string Name => "System Audio (WasapiLoopback)";

    public WasapiLoopbackRecorder(ILogger<WasapiLoopbackRecorder> logger)
    {
        _logger = logger;
    }

    public async Task<byte[]?> RecordAsync(int durationMs, CancellationToken ct = default)
    {
        if (!await _lock.WaitAsync(0, ct))
        {
            _logger.LogWarning("تسجيل صوت النظام المكتبي قيد التشغيل بالفعل.");
            return null;
        }

        string tempPath = Path.Combine(Path.GetTempPath(), $"loopback_{DateTime.Now:yyyyMMdd_HHmmss}.wav");
        WasapiLoopbackCapture? capture = null;
        WaveFileWriter? writer = null;

        try
        {
            _logger.LogInformation("🔊 بدء تسجيل صوت سطح المكتب (Wasapi Loopback) لمدة {dur} ثانية...", durationMs / 1000);

            capture = new WasapiLoopbackCapture();
            writer = new WaveFileWriter(tempPath, capture.WaveFormat);

            capture.DataAvailable += (s, a) =>
            {
                writer.Write(a.Buffer, 0, a.BytesRecorded);
            };

            var tcs = new TaskCompletionSource<bool>();
            capture.RecordingStopped += (s, a) =>
            {
                tcs.TrySetResult(true);
            };

            capture.StartRecording();

            using var combinedCts = CancellationTokenSource.CreateLinkedTokenSource(ct);
            combinedCts.CancelAfter(durationMs);

            try
            {
                await Task.Delay(durationMs, combinedCts.Token);
            }
            catch (OperationCanceledException) { }

            capture.StopRecording();
            await tcs.Task;

            writer.Dispose();
            writer = null;
            capture.Dispose();
            capture = null;

            if (File.Exists(tempPath) && new FileInfo(tempPath).Length > 1024)
            {
                byte[] audioData = await File.ReadAllBytesAsync(tempPath, ct);
                _logger.LogInformation("✅ اكتمل تسجيل صوت النظام بنجاح: {bytes} بايت", audioData.Length);
                return audioData;
            }

            _logger.LogWarning("⚠️ لم يتم رصد إشارة صوتية في مخرج السماعات أثناء فترة التسجيل.");
            return null;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "فشل تسجيل صوت سطح المكتب عبر WasapiLoopback.");
            return null;
        }
        finally
        {
            writer?.Dispose();
            capture?.Dispose();
            try
            {
                if (File.Exists(tempPath)) File.Delete(tempPath);
            }
            catch { }
            _lock.Release();
        }
    }
}
