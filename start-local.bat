@echo off
REM 本地联调启动脚本：启动本地 API 服务，并为雷电模拟器建立端口映射
REM 适用于 *.supabase.co 被网络阻断时，让 App 通过 adb reverse 访问电脑上的服务
cd /d "%~dp0"

netstat -ano | findstr /R /C:":8787 .*LISTENING" >nul
if errorlevel 1 (
  start "Ledger Local API" cmd /k "node server\local-api.mjs"
  timeout /t 2 /nobreak >nul
) else (
  echo 本地服务已在 8787 端口运行
)

set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if not exist "%ADB%" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not exist "%ADB%" set "ADB=D:\programe\Android\SDK\platform-tools\adb.exe"
if not exist "%ADB%" (
  echo 未找到 adb，请确认已安装 Android SDK
  goto :end
)

"%ADB%" devices | findstr /R /C:"emulator-.*device" >nul
if errorlevel 1 (
  echo 未检测到已启动的模拟器，请先启动雷电模拟器后重新运行本脚本
) else (
  "%ADB%" reverse tcp:8787 tcp:8787
  echo 端口映射完成：模拟器 127.0.0.1:8787 -^> 电脑本地服务
)

:end
pause
