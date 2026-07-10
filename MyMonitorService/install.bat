@echo off
chcp 65001 > nul
:: التحقق من صلاحيات المسؤول
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo [!] الرجاء تشغيل هذا السكربت كمسؤول (Run as Administrator).
    pause
    exit /b
)

echo [+] جاري تثبيت خدمة مراقبة النظام (MyMonitorService)...
echo.

:: إيقاف الخدمة في حال كانت تعمل سابقاً
sc query MyMonitorService >nul 2>&1
if %errorLevel% equ 0 (
    echo [+] تم العثور على الخدمة مثبتة مسبقاً، جاري إيقافها وحذفها لإعادة التثبيت...
    sc stop MyMonitorService >nul 2>&1
    timeout /t 2 /nobreak >nul
    sc delete MyMonitorService >nul 2>&1
    timeout /t 2 /nobreak >nul
)

:: إنشاء الخدمة وتعيينها لتبدأ تلقائياً
sc create MyMonitorService binPath= "%~dp0MyMonitorService.exe" start= auto DisplayName= "My System Monitor Service"
if %errorLevel% equ 0 (
    echo [+] تم تثبيت الخدمة بنجاح في سجلات النظام.
    
    :: وصف الخدمة في سجل الخدمات
    sc description MyMonitorService "خدمة لمراقبة موارد النظام والتطبيقات النشطة كل 30 ثانية وحفظ البيانات محلياً." >nul
    
    echo [+] جاري تشغيل الخدمة الآن...
    sc start MyMonitorService
    
    if %errorLevel% equ 0 (
        echo [+] تم تشغيل الخدمة بنجاح وهي تعمل الآن في الخلفية.
    ) else (
        echo [!] تم التثبيت بنجاح ولكن فشل تشغيل الخدمة تلقائياً. يمكنك تشغيلها يدوياً من خدمات ويندوز (services.msc).
    )
) else (
    echo [!] حدث خطأ أثناء محاولة إنشاء الخدمة. تأكد من أن الملف MyMonitorService.exe موجود في نفس المجلد ومبني بشكل صحيح.
)

echo.
pause
