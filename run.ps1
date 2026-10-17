param([switch]$Test)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $compiler = 'javac'
    $runtime = 'java'
    if ($env:JAVA_HOME) {
        $compiler = Join-Path $env:JAVA_HOME 'bin\javac.exe'
        $runtime = Join-Path $env:JAVA_HOME 'bin\java.exe'
    }
    if (-not (Get-Command $compiler -ErrorAction SilentlyContinue)) {
        throw 'Install JDK 11 or newer and set JAVA_HOME or add its bin directory to PATH. Then reopen your terminal.'
    }
    New-Item -ItemType Directory -Force out | Out-Null
    $sources = @(Get-ChildItem src\idle\*.java | ForEach-Object FullName)
    if ($Test) { $sources += @(Get-ChildItem tests\idle\*.java | ForEach-Object FullName) }
    & $compiler --release 11 -d out @sources
    if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }
    if ($Test) { & $runtime -cp out idle.GameStateTest }
    else { & $runtime -cp out idle.IdleGame }
    if ($LASTEXITCODE -ne 0) { throw 'Java exited with an error.' }
} finally { Pop-Location }
