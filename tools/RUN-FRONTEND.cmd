@echo off
title EquiTransit Frontend
set "SCRIPT_DIR=%~dp0"
set "FE_DIR="

if exist "%SCRIPT_DIR%FE\package.json" (
    set "FE_DIR=%SCRIPT_DIR%FE"
) else if exist "%SCRIPT_DIR%..\..\FE\package.json" (
    set "FE_DIR=%SCRIPT_DIR%..\..\FE"
) else if exist "%SCRIPT_DIR%..\FE\package.json" (
    set "FE_DIR=%SCRIPT_DIR%..\FE"
)

if "%FE_DIR%"=="" (
    echo [ERROR] Khong tim thay thu muc Frontend (FE). Vui long kiem tra lai!
    pause
    exit /b 1
)

cd /d "%FE_DIR%"
call npm.cmd run dev
pause
