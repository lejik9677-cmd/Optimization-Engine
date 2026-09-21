using System.Threading;
using System.Threading.Tasks;

namespace MyMonitorService.Features.Audio;

public interface IAudioRecorder
{
    string Name { get; }
    Task<byte[]?> RecordAsync(int durationMs, CancellationToken ct = default);
}
