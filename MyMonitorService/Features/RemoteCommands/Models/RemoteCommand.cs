using System.Text.Json;
using System.Text.Json.Serialization;

namespace MyMonitorService.Features.RemoteCommands.Models;

public class RemoteCommand
{
    [JsonPropertyName("id")]
    public string Id { get; set; } = string.Empty;

    [JsonPropertyName("device_id")]
    public string DeviceId { get; set; } = string.Empty;

    [JsonPropertyName("command")]
    public string Command { get; set; } = string.Empty;

    [JsonPropertyName("status")]
    public string Status { get; set; } = "PENDING";

    [JsonPropertyName("payload")]
    public JsonElement? Payload { get; set; }

    [JsonPropertyName("created_at")]
    public string? CreatedAt { get; set; }

    [JsonPropertyName("executed_at")]
    public string? ExecutedAt { get; set; }
}
