using System;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.Extensions.Logging;
using MyMonitorService.Core.Config;
using MyMonitorService.Core.Network;
using MyMonitorService.Features.RemoteCommands.Models;

namespace MyMonitorService.Features.RemoteCommands;

public class CommandPollingService
{
    private readonly SupabaseRestClient _restClient;
    private readonly ICommandExecutor _commandExecutor;
    private readonly ILogger<CommandPollingService> _logger;

    public CommandPollingService(
        SupabaseRestClient restClient,
        ICommandExecutor commandExecutor,
        ILogger<CommandPollingService> logger)
    {
        _restClient = restClient;
        _commandExecutor = commandExecutor;
        _logger = logger;
    }

    /// <summary>
    /// فحص وتنفيذ الأوامر المعلقة في Supabase
    /// </summary>
    public async Task PollAndExecuteAsync(CancellationToken ct = default)
    {
        try
        {
            string? jsonStr = await _restClient.GetPendingCommandsJsonAsync(
                DeviceIdentity.DeviceId,
                DeviceIdentity.LegacyDeviceId,
                ct);

            if (string.IsNullOrEmpty(jsonStr)) return;

            using var doc = JsonDocument.Parse(jsonStr);
            var root = doc.RootElement;
            if (root.ValueKind != JsonValueKind.Array) return;

            foreach (var element in root.EnumerateArray())
            {
                var cmd = new RemoteCommand
                {
                    Id = element.GetProperty("id").GetString() ?? "",
                    DeviceId = element.TryGetProperty("device_id", out var d) ? d.GetString() ?? "" : "",
                    Command = element.GetProperty("command").GetString() ?? "",
                    Status = element.GetProperty("status").GetString() ?? "PENDING"
                };

                if (element.TryGetProperty("payload", out var payload))
                {
                    cmd.Payload = payload.Clone();
                }

                _logger.LogInformation("⚡ اكتشاف أمر جديد: {cmd} (ID: {id})", cmd.Command, cmd.Id);

                bool success = await _commandExecutor.ExecuteAsync(cmd, ct);
                string newStatus = success ? "EXECUTED" : "FAILED";

                await _restClient.UpdateCommandStatusAsync(cmd.Id, newStatus, ct);
                _logger.LogInformation("✅ تم تحديث حالة الأمر {id} إلى {status}", cmd.Id, newStatus);
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "خطأ أثناء فحص أو تنفيذ الأوامر السحابية.");
        }
    }
}
