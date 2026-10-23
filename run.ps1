# NetPulse Application Launcher for Windows PowerShell
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "       Launching NetPulse Desktop       " -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Check if JAR exists, else build it
if (-not (Test-Path "target\netpulse-1.0.0.jar")) {
    Write-Host "Executable JAR not found. Building first..." -ForegroundColor Yellow
    .\build.ps1
}

Write-Host "Starting NetPulse Swing Dashboard on Java $(java -version 2>&1 | Select-Object -First 1)..." -ForegroundColor Green
java -jar target\netpulse-1.0.0.jar
