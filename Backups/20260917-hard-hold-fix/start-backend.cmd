@echo off
setlocal
cd /d "%~dp0Backend"
set "APP_CONFIG=%~dp0Backend\src\main\resources\"
set "APP_CONFIG=%APP_CONFIG:\=/%"
if defined JAVA_HOME (
  "%JAVA_HOME%\bin\java.exe" -jar "..\dist\SafePay.jar" "--spring.config.additional-location=file:%APP_CONFIG%" --spring.profiles.active=oracle
) else (
  java -jar "..\dist\SafePay.jar" "--spring.config.additional-location=file:%APP_CONFIG%" --spring.profiles.active=oracle
)
endlocal
