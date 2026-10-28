# Builds (if needed) and runs a Genealogy Archive System command against an installation file.
#   .\scripts\run.ps1 check-config --config=.\installation.yml
#   .\scripts\run.ps1 migrate      --config=.\installation.yml
#   .\scripts\run.ps1 serve        --config=.\installation.yml
#   .\scripts\run.ps1 worker       --config=.\installation.yml
# The database password comes from $env:GAS_DB_PASSWORD (or database.passwordFile).
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
$Jar = 'target\genealogy-archive.jar'
$stale = -not (Test-Path $Jar)
if (-not $stale) {
    $jarTime = (Get-Item $Jar).LastWriteTimeUtc
    $stale = [bool](Get-ChildItem -Recurse -File src, pom.xml | Where-Object { $_.LastWriteTimeUtc -gt $jarTime } | Select-Object -First 1)
}
if ($stale) {
    & .\mvnw.cmd -B -ntp -q package -DskipTests
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
& java -jar $Jar @args
exit $LASTEXITCODE
