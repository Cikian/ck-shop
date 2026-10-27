@echo off
setlocal
cd /d "%~dp0"
if "%PORT%"=="" set PORT=5001

REM Shop front-end PRODUCTION server. CikPier tunnel nuxt.frp.cikian.cn points here.
REM Port must stay 3001. Local dev stays on 3000 via npm run dev.
REM Why the phone needs this instead of the dev server: see the shop docs folder,
REM the write-up about mobile tap failure.
REM Usage:
REM   double click           start with the existing .output
REM   start-prod.bat build   rebuild first, then start
REM Closing this window stops the server.

if /I "%~1"=="build" goto build
if not exist ".output\server\index.mjs" goto build
goto start

:build
echo [1/2] building production bundle, please wait ...
call npm run build
if errorlevel 1 goto buildfail

:start
echo.
echo [2/2] production server on port %PORT%
echo       phone URL: https://nuxt.frp.cikian.cn
echo       close this window to stop.
echo.
call npm start
goto end

:buildfail
echo.
echo *** BUILD FAILED - server NOT started ***
pause

:end
pause
