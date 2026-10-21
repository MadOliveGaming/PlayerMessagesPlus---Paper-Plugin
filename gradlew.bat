@echo off
setlocal
set VERSION=9.2.1
set "BASE=%USERPROFILE%\.gradle\wrapper\manual"
set "DIST=%BASE%\gradle-%VERSION%"
if not exist "%DIST%\bin\gradle.bat" (
  if not exist "%BASE%" mkdir "%BASE%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip' -OutFile '%BASE%\gradle-%VERSION%-bin.zip'; Expand-Archive -Force '%BASE%\gradle-%VERSION%-bin.zip' '%BASE%'"
  if not exist "%DIST%\bin\gradle.bat" (
    echo Failed to download Gradle %VERSION%. Check your internet connection.
    exit /b 1
  )
)
call "%DIST%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
