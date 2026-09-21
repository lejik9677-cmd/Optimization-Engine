using System;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.Extensions.Logging;
using MyMonitorService.Core.Config;

namespace MyMonitorService.Core.Network;

public class SupabaseStorageClient
{
    private readonly HttpClient _httpClient;
    private readonly AppConfig _config;
    private readonly ILogger<SupabaseStorageClient> _logger;

    public SupabaseStorageClient(HttpClient httpClient, AppConfig config, ILogger<SupabaseStorageClient> logger)
    {
        _httpClient = httpClient;
        _config = config;
        _logger = logger;
    }

    /// <summary>
    /// رفع ملف ثنائي (صوت، صورة) إلى مسار مخصص في Supabase Storage
    /// </summary>
    public async Task<bool> UploadBinaryAsync(string relativePath, byte[] data, string contentType, CancellationToken ct = default)
    {
        try
        {
            string url = $"{_config.StorageObjectUrl}/{relativePath.TrimStart('/')}";

            using var request = new HttpRequestMessage(HttpMethod.Post, url);
            request.Headers.Add("apikey", _config.AnonKey);
            request.Headers.Add("Authorization", $"Bearer {_config.AnonKey}");
            request.Headers.Add("x-upsert", "true");

            request.Content = new ByteArrayContent(data);
            request.Content.Headers.ContentType = new MediaTypeHeaderValue(contentType);

            using var response = await _httpClient.SendAsync(request, ct);
            if (response.IsSuccessStatusCode)
            {
                _logger.LogInformation("✅ تم رفع الملف السحابي بنجاح: {path} ({size} B)", relativePath, data.Length);
                return true;
            }

            string err = await response.Content.ReadAsStringAsync(ct);
            _logger.LogWarning("❌ فشل رفع الملف السحابي {path} ({status}): {err}", relativePath, response.StatusCode, err);
            return false;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "استثناء أثناء رفع الملف السحابي إلى {path}", relativePath);
            return false;
        }
    }
}
