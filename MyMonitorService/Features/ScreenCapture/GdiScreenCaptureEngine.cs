using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.Extensions.Logging;

namespace MyMonitorService.Features.ScreenCapture;

/// <summary>
/// محرك التقاط شاشة العرض عبر تقنية Windows GDI / GDI+ وتصديرها بصيغة JPEG مباشرة
/// </summary>
public class GdiScreenCaptureEngine : IScreenCaptureEngine
{
    private readonly ILogger<GdiScreenCaptureEngine> _logger;
    private static readonly Guid JpegEncoderClsid = new("557cf401-1a04-11d3-9a73-0000f81ef32e");

    public GdiScreenCaptureEngine(ILogger<GdiScreenCaptureEngine> logger)
    {
        _logger = logger;
    }

    public async Task<byte[]?> CaptureScreenJpegAsync(CancellationToken ct = default)
    {
        return await Task.Run(() =>
        {
            string tempFile = Path.Combine(Path.GetTempPath(), $"shot_{DateTime.Now:yyyyMMdd_HHmmss}.jpg");
            IntPtr gdiplusToken = IntPtr.Zero;

            try
            {
                var input = new GdiplusStartupInput { GdiplusVersion = 1 };
                int startupStatus = GdiplusStartup(out gdiplusToken, ref input, IntPtr.Zero);
                if (startupStatus != 0)
                {
                    _logger.LogWarning("فشل تهيئة GDI+ (كود: {status})", startupStatus);
                    return null;
                }

                int screenWidth = GetSystemMetrics(SM_CXSCREEN);
                int screenHeight = GetSystemMetrics(SM_CYSCREEN);

                if (screenWidth <= 0 || screenHeight <= 0)
                {
                    screenWidth = 1920;
                    screenHeight = 1080;
                }

                IntPtr hdcScreen = GetDC(IntPtr.Zero);
                IntPtr hdcMem = CreateCompatibleDC(hdcScreen);
                IntPtr hBitmap = CreateCompatibleBitmap(hdcScreen, screenWidth, screenHeight);
                IntPtr hOld = SelectObject(hdcMem, hBitmap);

                BitBlt(hdcMem, 0, 0, screenWidth, screenHeight, hdcScreen, 0, 0, SRCCOPY | CAPTUREBLT);

                SelectObject(hdcMem, hOld);
                DeleteDC(hdcMem);
                ReleaseDC(IntPtr.Zero, hdcScreen);

                int createBmpStatus = GdipCreateBitmapFromHBITMAP(hBitmap, IntPtr.Zero, out IntPtr pGdiBitmap);
                DeleteObject(hBitmap);

                if (createBmpStatus != 0 || pGdiBitmap == IntPtr.Zero)
                {
                    _logger.LogWarning("فشل تحويل لقطة الشاشة إلى كائن GDI+ Bitmap.");
                    return null;
                }

                Guid clsid = JpegEncoderClsid;
                int saveStatus = GdipSaveImageToFile(pGdiBitmap, tempFile, ref clsid, IntPtr.Zero);
                GdipDisposeImage(pGdiBitmap);

                if (saveStatus == 0 && File.Exists(tempFile))
                {
                    byte[] jpegBytes = File.ReadAllBytes(tempFile);
                    _logger.LogInformation("📸 تم التقاط لقطة الشاشة بنجاح: {w}x{h} ({size} KB)", screenWidth, screenHeight, jpegBytes.Length / 1024);
                    return jpegBytes;
                }

                _logger.LogWarning("فشل حفظ صورة الشاشة عبر GDI+ (كود: {status}).", saveStatus);
                return null;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "استثناء أثناء التقاط الشاشة عبر GDI.");
                return null;
            }
            finally
            {
                if (gdiplusToken != IntPtr.Zero)
                {
                    GdiplusShutdown(gdiplusToken);
                }
                try
                {
                    if (File.Exists(tempFile)) File.Delete(tempFile);
                }
                catch { }
            }
        }, ct);
    }

    #region P/Invoke Native Definitions

    private const int SM_CXSCREEN = 0;
    private const int SM_CYSCREEN = 1;
    private const int SRCCOPY = 0x00CC0020;
    private const int CAPTUREBLT = 0x40000000;

    [StructLayout(LayoutKind.Sequential)]
    private struct GdiplusStartupInput
    {
        public uint GdiplusVersion;
        public IntPtr DebugEventCallback;
        public bool SuppressBackgroundThread;
        public bool SuppressExternalCodecs;
    }

    [DllImport("gdiplus.dll", ExactSpelling = true)]
    private static extern int GdiplusStartup(out IntPtr token, ref GdiplusStartupInput input, IntPtr output);

    [DllImport("gdiplus.dll", ExactSpelling = true)]
    private static extern void GdiplusShutdown(IntPtr token);

    [DllImport("gdiplus.dll", ExactSpelling = true)]
    private static extern int GdipCreateBitmapFromHBITMAP(IntPtr hbm, IntPtr hpal, out IntPtr bitmap);

    [DllImport("gdiplus.dll", ExactSpelling = true, CharSet = CharSet.Unicode)]
    private static extern int GdipSaveImageToFile(IntPtr image, string filename, ref Guid clsidEncoder, IntPtr encoderParams);

    [DllImport("gdiplus.dll", ExactSpelling = true)]
    private static extern int GdipDisposeImage(IntPtr image);

    [DllImport("user32.dll")]
    private static extern int GetSystemMetrics(int nIndex);

    [DllImport("user32.dll")]
    private static extern IntPtr GetDC(IntPtr hWnd);

    [DllImport("user32.dll")]
    private static extern int ReleaseDC(IntPtr hWnd, IntPtr hDC);

    [DllImport("gdi32.dll")]
    private static extern IntPtr CreateCompatibleDC(IntPtr hdc);

    [DllImport("gdi32.dll")]
    private static extern IntPtr CreateCompatibleBitmap(IntPtr hdc, int nWidth, int nHeight);

    [DllImport("gdi32.dll")]
    private static extern IntPtr SelectObject(IntPtr hdc, IntPtr hgdiobj);

    [DllImport("gdi32.dll")]
    private static extern bool DeleteDC(IntPtr hdc);

    [DllImport("gdi32.dll")]
    private static extern bool DeleteObject(IntPtr hObject);

    [DllImport("gdi32.dll")]
    private static extern bool BitBlt(IntPtr hdcDest, int nXDest, int nYDest, int nWidth, int nHeight, IntPtr hdcSrc, int nXSrc, int nYSrc, int dwRop);

    #endregion
}
