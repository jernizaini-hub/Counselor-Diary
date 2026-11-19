@echo off
title Diari Kaunselor — Versi Desktop (Windows)
cd /d "%~dp0"

where node >nul 2>nul
if %errorlevel% neq 0 (
  echo [RALAT] Node.js tidak ditemui pada komputer Windows ini.
  echo Sila pasang Node.js LTS daripada https://nodejs.org/ terlebih dahulu.
  pause
  exit /b 1
)

if not exist "node_modules" (
  echo Memasang pakej Diari Kaunselor Desktop buat kali pertama...
  call npm install
  if %errorlevel% neq 0 (
    echo [RALAT] Gagal memasang pakej npm.
    pause
    exit /b 1
  )
)

echo Membina aplikasi PWA...
call npm run build
if %errorlevel% neq 0 (
  echo [RALAT] Binaan aplikasi gagal.
  pause
  exit /b 1
)

echo.
echo ================================================================
echo  Diari Kaunselor Desktop sedia dibuka di: http://localhost:4173
echo  Tip: Klik ikon "Pasang" di bar alamat Chrome/Edge untuk jadikan
echo       aplikasi Windows tersendiri.
echo ================================================================
echo.
start "" "http://localhost:4173"
call npm run preview
