# NetPulse Test Execution Script for Windows PowerShell
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "     NetPulse Diagnostic Test Runner    " -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$ErrorActionPreference = "Stop"

# Ensure classes are built
if (-not (Test-Path "target\test-classes")) {
    Write-Host "Building project first..." -ForegroundColor Yellow
    .\build.ps1
}

# Run the test suite
java -cp "target\classes;target\test-classes" com.netpulse.TestRunner
exit $LASTEXITCODE
