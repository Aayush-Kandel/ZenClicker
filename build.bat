@echo off
setlocal

set "ROOT_DIR=%~dp0"
if "%ROOT_DIR:~-1%"=="\" set "ROOT_DIR=%ROOT_DIR:~0,-1%"

set "JAVA_HOME=%ROOT_DIR%\tools\jdk17"
set "SDK=%ROOT_DIR%\tools\android-sdk"
set "BT=%SDK%\build-tools\34.0.0"
set "PLATFORM=%SDK%\platforms\android-34\android.jar"
set "SRC=%ROOT_DIR%\app\src\main"
set "OUT=%ROOT_DIR%\build"

set "PATH=%JAVA_HOME%\bin;%PATH%"

if not exist "%BT%\aapt2.exe" (
    echo [ERROR] Android build tools not found at: %BT%
    echo Please run tools\setup_sdk.bat first.
    exit /b 1
)

echo Cleaning previous build...
if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%\classes" "%OUT%\dex" "%OUT%\aligned" "%OUT%\gen"

echo [1/6] Compile resources (aapt2)...
"%BT%\aapt2.exe" compile --dir "%SRC%\res" -o "%OUT%\res.zip"
if errorlevel 1 (
    echo [FAILED] Resource compilation failed.
    exit /b 1
)

echo [2/6] Link resources + manifest...
"%BT%\aapt2.exe" link -o "%OUT%\base.apk" -I "%PLATFORM%" --manifest "%SRC%\AndroidManifest.xml" ^
    --java "%OUT%\gen" --min-sdk-version 26 --target-sdk-version 34 ^
    --version-code 1 --version-name 1.1 ^
    "%OUT%\res.zip"
if errorlevel 1 (
    echo [FAILED] Resource linking failed.
    exit /b 1
)

echo [3/6] Compile Java...
dir /b /s "%OUT%\gen\*.java" "%SRC%\java\*.java" > "%OUT%\sources.txt"
"%JAVA_HOME%\bin\javac.exe" -source 11 -target 11 -nowarn -classpath "%PLATFORM%" -d "%OUT%\classes" @"%OUT%\sources.txt"
if errorlevel 1 (
    echo [FAILED] Java compilation failed.
    exit /b 1
)

echo [4/6] Dex (d8)...
dir /b /s "%OUT%\classes\*.class" > "%OUT%\classes.txt"
"%JAVA_HOME%\bin\java.exe" -cp "%BT%\lib\d8.jar" com.android.tools.r8.D8 ^
    --release --lib "%PLATFORM%" --min-api 26 --output "%OUT%\dex" @"%OUT%\classes.txt"
if errorlevel 1 (
    echo [FAILED] Dexing failed.
    exit /b 1
)

echo [5/6] Package and align...
copy /y "%OUT%\base.apk" "%OUT%\unsigned.apk" >nul
"%JAVA_HOME%\bin\jar.exe" -uf "%OUT%\unsigned.apk" -C "%OUT%\dex" classes.dex
if errorlevel 1 (
    echo [FAILED] Jar update failed.
    exit /b 1
)

"%BT%\zipalign.exe" -f 4 "%OUT%\unsigned.apk" "%OUT%\aligned\unsigned.apk"
if errorlevel 1 (
    echo [FAILED] Zipalign failed.
    exit /b 1
)

echo [6/6] Sign APK...
set "KEYSTORE=%ROOT_DIR%\debug.keystore"
if not exist "%KEYSTORE%" (
    echo Generating debug.keystore...
    "%JAVA_HOME%\bin\keytool.exe" -genkeypair -keystore "%KEYSTORE%" ^
        -storepass android -keypass android -alias zen ^
        -keyalg RSA -keysize 2048 -validity 10000 ^
        -dname "CN=Zen Clicker, O=Zen" >nul 2>&1
)

"%JAVA_HOME%\bin\java.exe" -jar "%BT%\lib\apksigner.jar" sign --ks "%KEYSTORE%" --ks-pass pass:android ^
    --key-pass pass:android --v4-signing-enabled false --out "%ROOT_DIR%\ZenClicker.apk" "%OUT%\aligned\unsigned.apk"
if errorlevel 1 (
    echo [FAILED] Signing failed.
    exit /b 1
)

echo.
echo =======================================================
echo  SUCCESS! Built: ZenClicker.apk (Ready to install)
echo =======================================================
