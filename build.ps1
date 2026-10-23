# NetPulse Build Script for Windows PowerShell
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   NetPulse Build & Packaging Script    " -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$ErrorActionPreference = "Stop"

# Locate JDK tools
$javac = "javac"
$java = "java"
$jar = "jar"
if (-not (Get-Command jar -ErrorAction SilentlyContinue)) {
    if (Test-Path "C:\Program Files\Java\jdk-18.0.2.1\bin\jar.exe") {
        $jar = "C:\Program Files\Java\jdk-18.0.2.1\bin\jar.exe"
    }
}

# 1. Clean & create output directories
Write-Host "`n[1/3] Creating output directories..." -ForegroundColor Yellow
New-Item -ItemType Directory -Force -Path "target\classes", "target\test-classes" | Out-Null

# 2. Compile Main Source Code
Write-Host "[2/3] Compiling application source code..." -ForegroundColor Yellow
$mainFiles = (Get-ChildItem -Recurse -Filter "*.java" src\main\java).FullName
& $javac -d target\classes $mainFiles
if ($LASTEXITCODE -ne 0) {
    Write-Error "Main compilation failed!"
    exit 1
}
Write-Host "   -> Main source compiled successfully." -ForegroundColor Green

# 3. Compile Test Source Code
Write-Host "[3/3] Compiling test suite..." -ForegroundColor Yellow
$testFiles = (Get-ChildItem -Recurse -Filter "*.java" src\test\java).FullName
& $javac -cp "target\classes" -d target\test-classes $testFiles
if ($LASTEXITCODE -ne 0) {
    Write-Error "Test compilation failed!"
    exit 1
}
Write-Host "   -> Test classes compiled successfully." -ForegroundColor Green

# 4. Create Executable JAR
Write-Host "`nPackaging executable JAR: target\netpulse-1.0.0.jar..." -ForegroundColor Yellow
$manifest = @"
Manifest-Version: 1.0
Main-Class: com.netpulse.Main
Created-By: NetPulse Build Tool

"@
$manifestPath = "target\MANIFEST.MF"
$manifest | Set-Content -Path $manifestPath -Encoding ascii

& $jar cfm target\netpulse-1.0.0.jar $manifestPath -C target\classes .
if ($LASTEXITCODE -eq 0) {
    Write-Host "   -> Package created: target\netpulse-1.0.0.jar" -ForegroundColor Green
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Build complete! To run the application:" -ForegroundColor Green
Write-Host "  .\run.ps1" -ForegroundColor White
Write-Host "Or:" -ForegroundColor Gray
Write-Host "  java -jar target\netpulse-1.0.0.jar" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Cyan
