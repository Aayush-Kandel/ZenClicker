@echo off
setlocal
set "TOOLS_DIR=%~dp0"
if "%TOOLS_DIR:~-1%"=="\" set "TOOLS_DIR=%TOOLS_DIR:~0,-1%"

set "JAVA_HOME=%TOOLS_DIR%\jdk17"
set "ANDROID_SDK_ROOT=%TOOLS_DIR%\android-sdk"
set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Accepting licenses...
(
echo y
echo y
echo y
echo y
echo y
echo y
echo y
echo y
) | "%ANDROID_SDK_ROOT%\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root="%ANDROID_SDK_ROOT%" --licenses

echo Installing platforms;android-34 and build-tools;34.0.0...
"%ANDROID_SDK_ROOT%\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root="%ANDROID_SDK_ROOT%" "platforms;android-34" "build-tools;34.0.0"

echo Done setting up Android SDK.
