using System.Threading;
using System.Threading.Tasks;

namespace MyMonitorService.Features.ScreenCapture;

public interface IScreenCaptureEngine
{
    Task<byte[]?> CaptureScreenJpegAsync(CancellationToken ct = default);
}
