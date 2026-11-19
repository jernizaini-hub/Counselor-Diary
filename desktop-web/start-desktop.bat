@echo off
title Diari Kaunselor — Versi Desktop (Windows)
cd /d "%~dp0"

echo ================================================================
echo   DIARI KAUNSELOR — VERSI DESKTOP WINDOWS (PWA)
echo ================================================================
echo.

where node >nul 2>nul
if %errorlevel% neq 0 (
  echo [RALAT] Node.js tidak ditemui pada komputer Windows ini.
  echo Sila muat turun dan pasang Node.js LTS daripada:
  echo   https://nodejs.org/
  echo.
  echo Selepas selesai pemasangan Node.js, tutup tetingkap ini dan
  echo klik dua kali start-desktop.bat sekali lagi.
  echo.
  pause
  exit /b 1
)

if not exist "node_modules" (
  echo [1/3] Memasang pakej Diari Kaunselor Desktop buat kali pertama...
  echo       (Ini hanya berlaku sekali sahaja, sila tunggu 1-2 minit)
  echo.
  call npm install
  if %errorlevel% neq 0 (
    echo.
    echo [RALAT] Gagal memasang pakej npm. Pastikan sambungan internet aktif.
    pause
    exit /b 1
  )
)

if not exist "dist\index.html" (
  echo [2/3] Membina aplikasi Desktop PWA buat kali pertama...
  echo.
  call npm run build
  if %errorlevel% neq 0 (
    echo.
    echo [RALAT] Binaan aplikasi gagal.
    pause
    exit /b 1
  )
)

echo [3/3] Melancarkan Diari Kaunselor di http://localhost:4173 ...
echo.
echo ================================================================
echo  * Biarkan tetingkap hitam ini terbuka semasa menggunakan aplikasi.
echo  * Log masuk dengan Akaun Google yang sama seperti di Android.
echo  * Tip: Klik ikon "Pasang" di penjuru kanan bar alamat Chrome/Edge
echo    untuk jadikan aplikasi Windows tersendiri di Desktop/Taskbar.
echo  * Untuk menutup pelayan, tutup sahaja tetingkap ini.
echo ================================================================
echo.

start "" /b cmd /c "timeout /t 3 /nobreak >nul && start http://localhost:4173"
call npm run preview

if %errorlevel% neq 0 (
  echo.
  echo [MAKLUMAN] Port 4173 mungkin sedang digunakan oleh tetingkap lain.
  echo Cuba buka terus http://localhost:4173 di pelayar Chrome/Edge anda.
  pause
)
