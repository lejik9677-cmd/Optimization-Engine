@echo off
chcp 65001 > nul
:: التحقق من صلاحيات المسؤول
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo [!] الرجاء تشغيل هذا السكربت كمسؤول (Run as Administrator).
    pause
    exit /b
)

echo [+] جاري إيقاف وإلغاء تثبيت خدمة مراقبة النظام (MyMonitorService)...
echo.

sc query MyMonitorService >nul 2>&1
if %errorLevel% neq 0 (
    echo [!] الخدمة MyMonitorService غير مثبتة بالأساس في النظام.
    pause
    exit /b
)

:: إيقاف الخدمة
echo [+] جاري إيقاف الخدمة...
sc stop MyMonitorService >nul 2>&1
timeout /t 2 /nobreak >nul

:: حذف الخدمة
echo [+] جاري حذف الخدمة من سجلات النظام...
sc delete MyMonitorService
if %errorLevel% equ 0 (
    echo [+] تم إلغاء تثبيت وحذف الخدمة بنجاح من جهازك!
) else (
    echo [!] حدث خطأ أثناء محاولة حذف الخدمة.
)

echo.
pause
