@echo off
title EquiTransit Fullstack Launcher
echo ========================================================
echo   EquiTransit - Cross-Border Horse Transportation System
echo ========================================================
echo.

set "SCRIPT_DIR=%~dp0"
set "BE_DIR="
set "FE_DIR="

if exist "%SCRIPT_DIR%BE\mvnw.cmd" (
    set "BE_DIR=%SCRIPT_DIR%BE"
) else if exist "%SCRIPT_DIR%..\mvnw.cmd" (
    set "BE_DIR=%SCRIPT_DIR%.."
) else if exist "%SCRIPT_DIR%mvnw.cmd" (
    set "BE_DIR=%SCRIPT_DIR%"
)

if exist "%SCRIPT_DIR%..\..\Code Pull\FE\package.json" (
    set "FE_DIR=%SCRIPT_DIR%..\..\Code Pull\FE"
) else if exist "C:\Users\mayum\Desktop\Code Pull\FE\package.json" (
    set "FE_DIR=C:\Users\mayum\Desktop\Code Pull\FE"
) else if exist "%SCRIPT_DIR%FE\package.json" (
    set "FE_DIR=%SCRIPT_DIR%FE"
) else if exist "%SCRIPT_DIR%..\..\FE\package.json" (
    set "FE_DIR=%SCRIPT_DIR%..\..\FE"
) else if exist "%SCRIPT_DIR%..\FE\package.json" (
    set "FE_DIR=%SCRIPT_DIR%..\FE"
)

if "%BE_DIR%"=="" (
    echo [ERROR] Khong tim thay thu muc Backend (BE). Vui long kiem tra lai!
    pause
    exit /b 1
)

echo Dang khoi dong Backend Spring Boot...
start "EquiTransit Backend" cmd /k "cd /d "%BE_DIR%" && mvnw.cmd spring-boot:run"

echo Dang doi Backend khoi dong (5 giay)...
timeout /t 5 /nobreak >nul

if not "%FE_DIR%"=="" (
    echo Dang khoi dong Frontend React Vite...
    start "EquiTransit Frontend" cmd /k "cd /d "%FE_DIR%" && npm.cmd run dev"
) else (
    echo [WARNING] Khong tim thay thu muc Frontend (FE), chi khoi dong Backend.
)

echo.
echo ========================================================
echo   Backend API:   http://localhost:8080
echo   Swagger UI:    http://localhost:8080/swagger-ui/index.html
echo   Frontend Web:  http://localhost:5173
echo ========================================================
echo.
pause
