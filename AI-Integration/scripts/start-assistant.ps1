$ErrorActionPreference = 'Stop'
$assistantRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $assistantRoot
$candidates = @()
if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
$candidates += 'C:\Program Files\Java\jdk-17\bin\java.exe'
$command = Get-Command java.exe -ErrorAction SilentlyContinue
if ($command) { $candidates += $command.Source }
$javaExe = $null
foreach ($candidate in $candidates) {
    if (Test-Path -LiteralPath $candidate) {
        $probe = New-Object System.Diagnostics.Process
        $probe.StartInfo.FileName = $candidate
        $probe.StartInfo.Arguments = '-version'
        $probe.StartInfo.UseShellExecute = $false
        $probe.StartInfo.RedirectStandardError = $true
        $probe.StartInfo.CreateNoWindow = $true
        [void]$probe.Start()
        $version = $probe.StandardError.ReadToEnd()
        $probe.WaitForExit()
        $probe.Dispose()
        if ($version -match 'version "(\d+)' -and [int]$Matches[1] -ge 17) { $javaExe = $candidate; break }
    }
}
if (-not $javaExe) { throw 'Java 17 or later is required. Set JAVA_HOME to your Java 17 directory.' }
$jar = Join-Path $assistantRoot 'dist\admin-assistant.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Build the assistant first: run build-assistant.cmd.' }
if (-not $env:SAFEPAY_DATABASE_PROPERTIES) { $env:SAFEPAY_DATABASE_PROPERTIES = [IO.Path]::GetFullPath((Join-Path $assistantRoot '..\Backend\src\main\resources\application.properties')) }
if (-not (Test-Path -LiteralPath $env:SAFEPAY_DATABASE_PROPERTIES)) { throw 'SafePay application.properties not found. Set SAFEPAY_DATABASE_PROPERTIES to the file used by your backend.' }
$port = if ($env:ASSISTANT_PORT) { $env:ASSISTANT_PORT } else { '8081' }
Write-Host "SafePay Admin Assistant: http://localhost:$port/assistant/"
Write-Host 'Keep the existing SafePay backend running. Stop this assistant with Ctrl+C.'
Write-Host 'Local AI requires Ollama and the qwen2.5:1.5b model (or OLLAMA_MODEL override).'
& $javaExe -jar $jar
exit $LASTEXITCODE
