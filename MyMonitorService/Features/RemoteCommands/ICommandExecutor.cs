using System.Threading;
using System.Threading.Tasks;
using MyMonitorService.Features.RemoteCommands.Models;

namespace MyMonitorService.Features.RemoteCommands;

public interface ICommandExecutor
{
    Task<bool> ExecuteAsync(RemoteCommand command, CancellationToken ct = default);
}
