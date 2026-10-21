@echo off
REM Cree l'APK publie : dist\IPTVPlayerTV-<version>.apk (version = versionName dans app\build.gradle.kts).
REM Avant : augmenter versionCode ET versionName. L'APK est signe avec la cle de keystore.properties (hors depot) :
REM sans elle, Android refuse la mise a jour des TV deja installees.
cd /d "%~dp0"
if not defined JAVA_HOME set "JAVA_HOME=%LOCALAPPDATA%\Android\jdk-17"
if not exist keystore.properties (
  echo ERREUR : keystore.properties introuvable ^(cle de signature, voir README^).
  pause
  exit /b 1
)
set VERSION=
for /f "tokens=2 delims==" %%v in ('findstr /c:"versionName =" app\build.gradle.kts') do set VERSION=%%v
set VERSION=%VERSION: =%
set VERSION=%VERSION:"=%
if not defined VERSION goto erreur
call "%~dp0gradlew.bat" assembleRelease || goto erreur
if not exist dist mkdir dist
copy /y app\build\outputs\apk\release\app-release.apk "dist\IPTVPlayerTV-%VERSION%.apk" >nul || goto erreur
echo.
echo Termine. APK : dist\IPTVPlayerTV-%VERSION%.apk
pause
exit /b 0

:erreur
echo.
echo ECHEC de la creation de l'APK.
pause
exit /b 1
