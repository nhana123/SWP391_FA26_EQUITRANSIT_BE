@echo off
title EquiTransit Backend
set "SCRIPT_DIR=%~dp0"
set "BE_DIR="

if exist "%SCRIPT_DIR%BE\mvnw.cmd" (
    set "BE_DIR=%SCRIPT_DIR%BE"
) else if exist "%SCRIPT_DIR%..\mvnw.cmd" (
    set "BE_DIR=%SCRIPT_DIR%.."
) else if exist "%SCRIPT_DIR%mvnw.cmd" (
    set "BE_DIR=%SCRIPT_DIR%"
)

if "%BE_DIR%"=="" (
    echo [ERROR] Khong tim thay thu muc Backend (BE). Vui long kiem tra lai!
    pause
    exit /b 1
)

cd /d "%BE_DIR%"
call mvnw.cmd spring-boot:run
pause
