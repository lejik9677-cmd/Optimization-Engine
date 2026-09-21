using System;

namespace MyMonitorService.Core.Config;

/// <summary>
/// إدارة هوية الجهاز وتثبيت المعرف الفريد ليتطابق مع السجلات السحابية
/// </summary>
public static class DeviceIdentity
{
    /// <summary>
    /// المعرّف الأساسي المعتمد في Supabase ولوحة التحكم (مثل: DESKTOP-C91364L)
    /// </summary>
    public static string DeviceId => Environment.MachineName.ToUpperInvariant();

    /// <summary>
    /// اسم الجهاز المعروض في لوحة التحكم
    /// </summary>
    public static string DisplayName => $"🖥️ {DeviceId}";

    /// <summary>
    /// نوع المنصة
    /// </summary>
    public static string Platform => "windows";

    /// <summary>
    /// المعرف القديم المسبوق بـ windows- للاستعلام التوافقي إن لزم
    /// </summary>
    public static string LegacyDeviceId => $"windows-{Environment.MachineName.ToLowerInvariant()}";
}
