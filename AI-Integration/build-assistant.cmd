@echo off
setlocal
cd /d "%~dp0"
if not defined JAVA_HOME if exist "C:\Program Files\Java\jdk-17\bin\java.exe" set "JAVA_HOME=C:\Program Files\Java\jdk-17"
if exist "..\Backend\mvnw.cmd" (
  call "..\Backend\mvnw.cmd" -f "%~dp0pom.xml" clean package
) else (
  call mvn -f "%~dp0pom.xml" clean package
)
if errorlevel 1 exit /b 1
if not exist "dist" mkdir "dist"
copy /y "target\admin-assistant.jar" "dist\admin-assistant.jar" >nul
echo Build ready. Run start-assistant.cmd.
endlocal
