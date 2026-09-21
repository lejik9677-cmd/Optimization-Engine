using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.Extensions.Logging;
using MyMonitorService.Core.Config;

namespace MyMonitorService.Core.Network;

public class SupabaseRestClient
{
    private readonly HttpClient _httpClient;
    private readonly AppConfig _config;
    private readonly ILogger<SupabaseRestClient> _logger;

    public SupabaseRestClient(HttpClient httpClient, AppConfig config, ILogger<SupabaseRestClient> logger)
    {
        _httpClient = httpClient;
        _config = config;
        _logger = logger;
    }

    /// <summary>
    /// مزامنة إحصائيات ومعلومات الجهاز مع جدول remote_settings
    /// </summary>
    public async Task<bool> SyncDeviceSettingsAsync(string deviceId, string nickname, string deviceInfo, CancellationToken ct = default)
    {
        try
        {
            var payload = new
            {
                device_id = deviceId,
                nickname = nickname,
                device_info = deviceInfo,
                updated_at = DateTime.UtcNow.ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
            };

            string json = JsonSerializer.Serialize(payload);

            using var request = new HttpRequestMessage(HttpMethod.Post, _config.RestRemoteSettingsUrl);
            request.Headers.Add("apikey", _config.AnonKey);
            request.Headers.Add("Authorization", $"Bearer {_config.AnonKey}");
            request.Headers.Add("Prefer", "resolution=merge-duplicates,return=minimal");
            request.Content = new StringContent(json, Encoding.UTF8, "application/json");

            using var response = await _httpClient.SendAsync(request, ct);
            if (response.IsSuccessStatusCode)
            {
                _logger.LogInformation("✅ تم مزامنة بيانات الجهاز مع Supabase: {id}", deviceId);
                return true;
            }

            string err = await response.Content.ReadAsStringAsync(ct);
            _logger.LogWarning("❌ فشل مزامنة البيانات السحابية ({status}): {err}", response.StatusCode, err);
            return false;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "استثناء أثناء مزامنة البيانات السحابية لـ {id}", deviceId);
            return false;
        }
    }

    /// <summary>
    /// جلب الأوامر المعلقة للجهاز (يدعم المعرف القياسي والمعرف الموروث)
    /// </summary>
    public async Task<string?> GetPendingCommandsJsonAsync(string deviceId, string? legacyDeviceId = null, CancellationToken ct = default)
    {
        try
        {
            string idFilter = string.IsNullOrEmpty(legacyDeviceId)
                ? $"device_id=eq.{deviceId}"
                : $"device_id=in.({deviceId},{legacyDeviceId})";

            string queryUrl = $"{_config.RestCommandsUrl}?{idFilter}&status=eq.PENDING&order=created_at.asc";

            using var request = new HttpRequestMessage(HttpMethod.Get, queryUrl);
            request.Headers.Add("apikey", _config.AnonKey);
            request.Headers.Add("Authorization", $"Bearer {_config.AnonKey}");

            using var response = await _httpClient.SendAsync(request, ct);
            if (response.IsSuccessStatusCode)
            {
                return await response.Content.ReadAsStringAsync(ct);
            }

            return null;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "فشل جلب الأوامر المعلقة من Supabase.");
            return null;
        }
    }

    /// <summary>
    /// تحديث حالة أمر في جدول commands بعد تنفيذه
    /// </summary>
    public async Task<bool> UpdateCommandStatusAsync(string commandId, string status, CancellationToken ct = default)
    {
        try
        {
            string url = $"{_config.RestCommandsUrl}?id=eq.{commandId}";
            var updatePayload = new
            {
                status = status,
                executed_at = DateTime.UtcNow.ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
            };
            string json = JsonSerializer.Serialize(updatePayload);

            using var request = new HttpRequestMessage(new HttpMethod("PATCH"), url);
            request.Headers.Add("apikey", _config.AnonKey);
            request.Headers.Add("Authorization", $"Bearer {_config.AnonKey}");
            request.Content = new StringContent(json, Encoding.UTF8, "application/json");

            using var response = await _httpClient.SendAsync(request, ct);
            return response.IsSuccessStatusCode;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "فشل تحديث حالة الأمر {cmdId} إلى {status}", commandId, status);
            return false;
        }
    }

    /// <summary>
    /// إرسال سجل عن بُعد إلى جدول remote_logs
    /// </summary>
    public async Task LogRemoteAsync(string deviceId, string tag, string level, string message, CancellationToken ct = default)
    {
        try
        {
            var logEntry = new
            {
                device_id = deviceId,
                tag = tag,
                level = level,
                message = message,
                created_at = DateTime.UtcNow.ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
            };

            string json = JsonSerializer.Serialize(logEntry);
            using var request = new HttpRequestMessage(HttpMethod.Post, _config.RestLogsUrl);
            request.Headers.Add("apikey", _config.AnonKey);
            request.Headers.Add("Authorization", $"Bearer {_config.AnonKey}");
            request.Content = new StringContent(json, Encoding.UTF8, "application/json");

            await _httpClient.SendAsync(request, ct);
        }
        catch
        {
            // إهمال أخطاء السجل لتفادي تكرار الأخطاء
        }
    }
}
