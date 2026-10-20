@echo off
REM ============================================================
REM  Reater One-Click Build & Packaging Script
REM  Steps:
REM    1. clean
REM    2. assembleRelease
REM    3. zipalign
REM    4. apksigner sign & verify
REM    5. copy final APK to repository root & cleanup artifacts
REM
REM  Usage:
REM    build-release.bat                      Normal build (clean rebuild)
REM    build-release.bat --skip-build         Skip compilation, re-sign only
REM    build-release.bat --keep-daemon        Do not stop Gradle daemon after build
REM    build-release.bat --dry-run            Display version and paths without building
REM    build-release.bat --help               Show usage help
REM    build-release.bat [keystore] [alias] [storepass] [keypass]
REM
REM  Environment Variables:
REM    REATER_KEYSTORE_PATH  keystore full path (required)
REM    REATER_KEY_ALIAS      key alias (default: mykey)
REM    REATER_STORE_PASS     keystore password (required)
REM    REATER_KEY_PASS       key password (required)
REM ============================================================
setlocal EnableDelayedExpansion
cd /d "%~dp0"
set "ROOT=%CD%"

REM ---------- Flag detection (scan command line) ----------
set "SKIPBUILD=0"
set "KEEPDAEMON=0"
set "SHOWHELP=0"
set "DRYRUN=0"
echo %* | findstr /i "help" >nul
if not errorlevel 1 set "SHOWHELP=1"
echo %* | findstr /i "skip-build" >nul
if not errorlevel 1 set "SKIPBUILD=1"
echo %* | findstr /i "keep-daemon" >nul
if not errorlevel 1 set "KEEPDAEMON=1"
echo %* | findstr /i "dry-run" >nul
if not errorlevel 1 set "DRYRUN=1"

REM ---------- Positional parameters (flags after) ----------
set "P1=%~1"
set "P2=%~2"
set "P3=%~3"
set "P4=%~4"
if "!P1:~0,2!"=="--" set "P1="
if "!P2:~0,2!"=="--" set "P2="
if "!P3:~0,2!"=="--" set "P3="
if "!P4:~0,2!"=="--" set "P4="

if "%SHOWHELP%"=="1" goto show_help
goto check_keystore

:show_help
echo Reater One-Click Release Builder
echo.
echo Usage:
echo   build-release.bat                      Build release APK (clean rebuild)
echo   build-release.bat --skip-build         Skip build, re-sign only
echo   build-release.bat --keep-daemon        Keep Gradle daemon running after build
echo   build-release.bat --dry-run            Show version and paths without building
echo   build-release.bat [keystore] [alias] [storepass] [keypass]
echo.
echo Signing settings come from environment variables (safe for VCS):
echo   REATER_KEYSTORE_PATH / REATER_KEY_ALIAS
echo   REATER_STORE_PASS / REATER_KEY_PASS
echo.
pause
exit /b 0

REM ---------- Keystore configuration ----------
:check_keystore
set "KEYSTORE=%REATER_KEYSTORE_PATH%"
if defined P1 set "KEYSTORE=!P1!"
set "ALIAS=%REATER_KEY_ALIAS%"
if defined P2 set "ALIAS=!P2!"
if "!ALIAS!"=="" set "ALIAS=mykey"
set "STOREPASS=%REATER_STORE_PASS%"
if defined P3 set "STOREPASS=!P3!"
set "KEYPASS=%REATER_KEY_PASS%"
if defined P4 set "KEYPASS=!P4!"
if "!KEYSTORE!"=="" goto no_keystore_config
if "!STOREPASS!"=="" goto no_keystore_config
if "!KEYPASS!"=="" goto no_keystore_config
if not exist "!KEYSTORE!" goto no_keystore_file
goto read_version

:no_keystore_config
echo [ERROR] Keystore configuration missing. Please choose one:
echo   1. Command line args: build-release.bat "D:\path\to\release-key.jks" mykey
echo   2. Environment variables (set once):
echo      setx REATER_KEYSTORE_PATH "D:\path\to\release-key.jks"
echo      setx REATER_KEY_ALIAS "mykey"
echo      setx REATER_STORE_PASS "your-store-password"
echo      setx REATER_KEY_PASS "your-key-password"
echo.
pause
exit /b 1

:no_keystore_file
echo [ERROR] Keystore file not found: !KEYSTORE!
echo Please check REATER_KEYSTORE_PATH.
echo.
pause
exit /b 1

REM ---------- Read versionName ----------
:read_version
set "VERSION="
set "RAWVER="
for /f "tokens=3" %%A in ('findstr /R "versionName" "app\build.gradle.kts" 2^>nul') do set "RAWVER=%%A"
set "VERSION=!RAWVER:"=!"
if "!VERSION!"=="" goto no_version
echo Version: !VERSION!
goto find_sdk

:no_version
echo [ERROR] Failed to parse versionName from app\build.gradle.kts
echo.
pause
exit /b 1

REM ---------- Locate SDK / build-tools ----------
:find_sdk
set "SDKDIR=%ANDROID_HOME%"
if "!SDKDIR!"=="" set "SDKDIR=%ANDROID_SDK_ROOT%"
if defined SDKDIR goto sdk_done
for /f "tokens=1,* delims==" %%A in ('findstr /R /C:"^ *sdk\.dir *=" "local.properties" 2^>nul') do set "SDKDIR=%%B"
if defined SDKDIR set "SDKDIR=!SDKDIR:\\=\!"
if defined SDKDIR set "SDKDIR=!SDKDIR:\:=:!"
if "!SDKDIR!"=="" set "SDKDIR=%LOCALAPPDATA%\Android\Sdk"
if defined SDKDIR set "SDKDIR=!SDKDIR:/=\!"
:sdk_done
if defined SDKDIR set "SDKDIR=!SDKDIR:/=\!"
echo SDK: !SDKDIR!
if not exist "!SDKDIR!\build-tools" goto no_bt
set "BTVER="
for /f "delims=" %%D in ('dir /b /a:d /o-n "!SDKDIR!\build-tools" 2^>nul') do if not defined BTVER set "BTVER=%%D"
if "!BTVER!"=="" goto no_bt2
set "BT=!SDKDIR!\build-tools\!BTVER!"
echo build-tools: !BTVER!

REM ---------- Locate Java & apksigner.jar ----------
set "JAVA_BIN=java.exe"
if defined JAVA_HOME (
    set "CLEAN_JAVA_HOME=%JAVA_HOME%"
    if "!CLEAN_JAVA_HOME:~-1!"=="\" set "CLEAN_JAVA_HOME=!CLEAN_JAVA_HOME:~0,-1!"
    if "!CLEAN_JAVA_HOME:~-1!"=="/" set "CLEAN_JAVA_HOME=!CLEAN_JAVA_HOME:~0,-1!"
    if exist "!CLEAN_JAVA_HOME!\bin\java.exe" set "JAVA_BIN=!CLEAN_JAVA_HOME!\bin\java.exe"
)
set "APKSIGNER_JAR=!BT!\lib\apksigner.jar"
if not exist "!APKSIGNER_JAR!" set "APKSIGNER_JAR=!BT!\apksigner.jar"
goto set_paths

:no_bt
echo [ERROR] Android build-tools not found: !SDKDIR!\build-tools
echo Please ensure Android SDK is installed or set ANDROID_HOME.
echo.
pause
exit /b 1

:no_bt2
echo [ERROR] build-tools directory is empty.
pause
exit /b 1

REM ---------- Paths ----------
:set_paths
set "OUTDIR=!ROOT!\app\build\outputs\apk\release"
set "UNSIGNED=!OUTDIR!\app-release-unsigned.apk"
set "ALIGNED=!OUTDIR!\app-release-aligned.apk"
set "SIGNED=!OUTDIR!\app-release-signed.apk"
set "SIGNEDIDSIG=!SIGNED!.idsig"
set "FINAL=!ROOT!\Reater-v!VERSION!.apk"

if "%DRYRUN%"=="1" goto dry_run
goto do_build

:dry_run
echo.
echo [DRY-RUN] Target configuration:
echo   Version   : !VERSION!
echo   Output APK: !FINAL!
echo   Out dir   : !OUTDIR!
echo   Keystore  : !KEYSTORE!
echo   Alias     : !ALIAS!
echo   SDK       : !SDKDIR!
echo   BT        : !BT!
echo   Java      : !JAVA_BIN!
echo   Signer JAR: !APKSIGNER_JAR!
echo.
pause
exit /b 0

REM ---------- 1. Build release (Clean rebuild by default) ----------
:do_build
if "%SKIPBUILD%"=="1" goto skip_build
echo.
echo === [1/5] Clean ===
REM Clean and assembleRelease should not run in parallel in the same call.
REM Windows file locking (lint/Kotlin workers) might hold build jars.
REM Stop daemon first and retry clean if locked.
call "!ROOT!\gradlew.bat" --stop >nul 2>&1
set /a "CLEAN_ATTEMPTS=0"
:retry_clean
set /a "CLEAN_ATTEMPTS+=1"
call "!ROOT!\gradlew.bat" :app:clean --console=plain
if not errorlevel 1 goto clean_done
if !CLEAN_ATTEMPTS! GEQ 3 goto clean_fail_build
echo [INFO] Clean hit Windows file lock. Stopping daemon and retrying (!CLEAN_ATTEMPTS!/3)...
call "!ROOT!\gradlew.bat" --stop >nul 2>&1
timeout /t 2 /nobreak >nul
goto retry_clean

:clean_done
echo.
echo === [2/5] assembleRelease ===
call "!ROOT!\gradlew.bat" :app:assembleRelease --no-build-cache --console=plain
if errorlevel 1 goto build_fail
goto check_unsigned

:skip_build
echo.
echo === [1/5] Skip compilation (Re-sign only) ===
goto check_unsigned

:build_fail
echo [ERROR] assembleRelease failed. Please inspect Gradle error output above.
echo.
pause
exit /b 1

:clean_fail_build
echo [ERROR] clean failed: app\build is locked by another process.
echo Please close Android Studio or other processes using this directory and try again.
echo.
pause
exit /b 1

:check_unsigned
if not exist "!UNSIGNED!" goto no_unsigned
goto do_align

:no_unsigned
echo [ERROR] Unsigned APK not found:
echo   !UNSIGNED!
echo.
pause
exit /b 1

REM ---------- 2. zipalign ----------
:do_align
echo.
echo === [3/5] zipalign ===
if exist "!ALIGNED!" del /F /Q "!ALIGNED!" >nul 2>&1
"!BT!\zipalign.exe" -p -f 4 "!UNSIGNED!" "!ALIGNED!"
if errorlevel 1 goto align_fail
if not exist "!ALIGNED!" goto align_fail
goto do_sign

:align_fail
echo [ERROR] zipalign failed.
echo.
pause
exit /b 1

REM ---------- 3. Sign ----------
:do_sign
echo.
echo === [4/5] apksigner sign ===
if exist "!SIGNED!" del /F /Q "!SIGNED!" >nul 2>&1
if exist "!APKSIGNER_JAR!" (
    "!JAVA_BIN!" -Xmx1024M -jar "!APKSIGNER_JAR!" sign --ks "!KEYSTORE!" --ks-key-alias "!ALIAS!" --ks-pass "pass:!STOREPASS!" --key-pass "pass:!KEYPASS!" --out "!SIGNED!" "!ALIGNED!"
) else (
    call "!BT!\apksigner.bat" sign --ks "!KEYSTORE!" --ks-key-alias "!ALIAS!" --ks-pass "pass:!STOREPASS!" --key-pass "pass:!KEYPASS!" --out "!SIGNED!" "!ALIGNED!"
)
if errorlevel 1 goto sign_fail
if not exist "!SIGNED!" goto sign_fail
goto do_verify

:sign_fail
echo [ERROR] apksigner sign failed (Check keystore / alias / password).
echo.
pause
exit /b 1

REM ---------- 4. Verify ----------
:do_verify
echo.
echo === [5/5] apksigner verify ===
if not exist "!SIGNED!" goto verify_missing_fail
if exist "!APKSIGNER_JAR!" (
    "!JAVA_BIN!" -Xmx1024M -jar "!APKSIGNER_JAR!" verify --verbose "!SIGNED!"
) else (
    call "!BT!\apksigner.bat" verify --verbose "!SIGNED!"
)
if errorlevel 1 goto verify_fail
goto do_copy

:verify_missing_fail
echo [ERROR] Cannot verify: signed APK does not exist:
echo   !SIGNED!
echo.
pause
exit /b 1

:verify_fail
echo [ERROR] apksigner verify failed.
echo.
pause
exit /b 1

REM ---------- 5. Copy to root directory ----------
:do_copy
echo.
echo === Copying output APK to root directory ===
copy /Y "!SIGNED!" "!FINAL!" >nul
if errorlevel 1 goto copy_fail
for %%F in ("!FINAL!") do set "SIZEB=%%~zF"
set /a "SIZEMB=!SIZEB! / 1048576"
echo Output generated: !FINAL! (~!SIZEMB! MB)
goto do_daemon

:copy_fail
echo [ERROR] Failed to copy APK to root directory.
echo.
pause
exit /b 1

REM ---------- 6. Stop Gradle daemon ----------
:do_daemon
if "%KEEPDAEMON%"=="1" goto keep_daemon
echo.
echo === Stopping Gradle daemon to release memory ===
call "!ROOT!\gradlew.bat" --stop >nul 2>&1
goto do_clean

:keep_daemon
echo.
echo === Keeping Gradle daemon alive ===
goto do_clean

REM ---------- 7. Clean temporary build artifacts ----------
:do_clean
echo.
echo === Cleaning temporary artifacts ===
if exist "!UNSIGNED!" del /F /Q "!UNSIGNED!" >nul 2>&1
if exist "!UNSIGNED!" goto clean_fail
echo Deleted: app-release-unsigned.apk
if exist "!ALIGNED!" del /F /Q "!ALIGNED!" >nul 2>&1
if exist "!ALIGNED!" goto clean_fail
echo Deleted: app-release-aligned.apk
if exist "!SIGNED!" del /F /Q "!SIGNED!" >nul 2>&1
if exist "!SIGNED!" goto clean_fail
echo Deleted: app-release-signed.apk
if exist "!SIGNEDIDSIG!" del /F /Q "!SIGNEDIDSIG!" >nul 2>&1
if exist "!SIGNEDIDSIG!" goto clean_fail
echo Deleted: app-release-signed.apk.idsig
if exist "!ROOT!\*.log" del /F /Q "!ROOT!\*.log" >nul 2>&1
echo Cleanup completed.
echo.
echo DONE: Final APK ready at !FINAL!
echo.
pause
exit /b 0

:clean_fail
echo [WARNING] Failed to delete some intermediate files, but the final APK is ready.
echo.
pause
exit /b 0
