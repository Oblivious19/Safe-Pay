@echo off
setlocal
cd /d "%~dp0Frontend\SafePayJet"
call npx ojet serve --server-port=8000
endlocal
