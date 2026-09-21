using System;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.Extensions.Logging;
using NAudio.Wave;

namespace MyMonitorService.Features.Audio;

/// <summary>
/// تسجيل صوت الميكروفون عبر محرك NAudio WaveInEvent (16kHz Mono 16-bit PCM)
/// </summary>
public class NaudioWaveRecorder : IAudioRecorder
{
    private readonly ILogger<NaudioWaveRecorder> _logger;
    private readonly SemaphoreSlim _lock = new(1, 1);

    public string Name => "Microphone (WaveInEvent)";

    public NaudioWaveRecorder(ILogger<NaudioWaveRecorder> logger)
    {
        _logger = logger;
    }

    public async Task<byte[]?> RecordAsync(int durationMs, CancellationToken ct = default)
    {
        if (!await _lock.WaitAsync(0, ct))
        {
            _logger.LogWarning("التسجيل الصوتي للميكروفون يعمل حالياً — تم تخطي الطلب المكرر.");
            return null;
        }

        string tempPath = Path.Combine(Path.GetTempPath(), $"mic_{DateTime.Now:yyyyMMdd_HHmmss}.wav");
        WaveInEvent? waveIn = null;
        WaveFileWriter? writer = null;

        try
        {
            _logger.LogInformation("🎙️ بدء تسجيل الميكروفون لمدة {dur} ثانية...", durationMs / 1000);

            waveIn = new WaveInEvent
            {
                WaveFormat = new WaveFormat(16000, 16, 1) // 16kHz Mono 16-bit PCM
            };

            writer = new WaveFileWriter(tempPath, waveIn.WaveFormat);

            waveIn.DataAvailable += (s, a) =>
            {
                writer.Write(a.Buffer, 0, a.BytesRecorded);
            };

            var tcs = new TaskCompletionSource<bool>();
            waveIn.RecordingStopped += (s, a) =>
            {
                tcs.TrySetResult(true);
            };

            waveIn.StartRecording();

            // الانتظار للمدة المحددة أو حتى الإلغاء
            using var combinedCts = CancellationTokenSource.CreateLinkedTokenSource(ct);
            combinedCts.CancelAfter(durationMs);

            try
            {
                await Task.Delay(durationMs, combinedCts.Token);
            }
            catch (OperationCanceledException)
            {
                // اكتمال المدة أو إلغاء خارجي
            }

            waveIn.StopRecording();
            await tcs.Task;

            writer.Dispose();
            writer = null;
            waveIn.Dispose();
            waveIn = null;

            if (File.Exists(tempPath) && new FileInfo(tempPath).Length > 44) // تجاوز ترويسة WAV الفارغة
            {
                byte[] audioData = await File.ReadAllBytesAsync(tempPath, ct);
                _logger.LogInformation("✅ اكتمل تسجيل الميكروفون بنجاح: {bytes} بايت", audioData.Length);
                return audioData;
            }

            _logger.LogWarning("⚠️ تم إنتاج ملف تسجيل فارغ أو لم يتم التقاط بيانات.");
            return null;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "خطأ أثناء تسجيل الميكروفون عبر NAudio.");
            return null;
        }
        finally
        {
            writer?.Dispose();
            waveIn?.Dispose();
            try
            {
                if (File.Exists(tempPath)) File.Delete(tempPath);
            }
            catch { }
            _lock.Release();
        }
    }
}
