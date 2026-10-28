# Runs the Fast Development Suite (unit tests) and the Phase-Gate Suite (PostgreSQL integration
# tests). Integration tests need a PostgreSQL server you can create throwaway databases on:
#   $env:GAS_TEST_DB_URL = 'jdbc:postgresql://localhost:5432/postgres'
#   $env:GAS_TEST_DB_USER = '<role with CREATEDB>'
#   $env:GAS_TEST_DB_PASSWORD = '<password>'
# Unit tests only (no database):  .\mvnw.cmd test
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

$missing = $false
foreach ($v in 'GAS_TEST_DB_URL', 'GAS_TEST_DB_USER', 'GAS_TEST_DB_PASSWORD') {
    if (-not [Environment]::GetEnvironmentVariable($v)) {
        Write-Error "Missing environment variable $v (see README 'Running the tests')." -ErrorAction Continue
        $missing = $true
    }
}
if ($missing) { exit 2 }

& .\mvnw.cmd -B -ntp clean verify @args
exit $LASTEXITCODE
