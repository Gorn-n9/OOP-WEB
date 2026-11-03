param([int]$Port = 8765)
$ErrorActionPreference = 'Stop'
$compiler = Get-Command javac -ErrorAction SilentlyContinue
if ($compiler) {
    $jdkBin = Split-Path $compiler.Source
} elseif ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME/bin/javac.exe")) {
    $jdkBin = "$env:JAVA_HOME/bin"
} else {
    $jdk = Get-ChildItem "$PSScriptRoot/../.tools" -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $jdk) { throw 'JDK 21+ is required.' }
    $jdkBin = Join-Path $jdk.FullName 'bin'
}

Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Force build | Out-Null
    & "$jdkBin/javac.exe" -encoding UTF-8 --release 21 -d build Main.java Delivery.java Examples.java
    if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }
    & "$jdkBin/java.exe" -cp build Main $Port
    if ($LASTEXITCODE -ne 0) { throw 'Server exited with an error.' }
} finally {
    Pop-Location
}
