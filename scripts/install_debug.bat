@echo off
set JAVA_HOME=D:\Program Files\Android\Android Studio1\jbr
set PATH=D:\Program_Files\Android\Sdk\platform-tools;%PATH%
cd /d d:\My_Project\Hoshi-Reader-Android
call .\gradlew.bat :app:assembleDebug --no-daemon --console=plain > build_debug.log 2>&1
echo BUILD_EXIT=%ERRORLEVEL%
if not %ERRORLEVEL%==0 (
  echo BUILD FAILED
  exit /b %ERRORLEVEL%
)
adb -s 381QYGEM226CZ install -r app\build\outputs\apk\debug\app-debug.apk
echo INSTALL_EXIT=%ERRORLEVEL%
