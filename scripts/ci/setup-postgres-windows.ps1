# CI only: installs the requested PostgreSQL major version on a native Windows runner (EDB build via
# Chocolatey), as a Windows service with a random per-run password, and exports
# GAS_TEST_DB_URL / GAS_TEST_DB_USER / GAS_TEST_DB_PASSWORD and GAS_SMOKE_DB_URL to later steps.
param(
    [Parameter(Mandatory = $true)][int]$Version
)
$ErrorActionPreference = 'Stop'
$Port = 5499

$Password = -join ((1..48) | ForEach-Object { '{0:x}' -f (Get-Random -Maximum 16) })
Write-Output "::add-mask::$Password"

# Pre-installed PostgreSQL on the runner image (if any) must not answer on our port.
choco install "postgresql$Version" -y --no-progress --params "/Password:$Password /Port:$Port"
if ($LASTEXITCODE -ne 0) { throw "Chocolatey install of postgresql$Version failed ($LASTEXITCODE)" }

$Bin = "C:\Program Files\PostgreSQL\$Version\bin"
if (-not (Test-Path "$Bin\psql.exe")) { throw "psql.exe not found in $Bin" }

$env:PGPASSWORD = $Password
& "$Bin\psql.exe" -h localhost -p $Port -U postgres -d postgres -v ON_ERROR_STOP=1 -tAc "SELECT version()"
if ($LASTEXITCODE -ne 0) { throw "Cannot connect to PostgreSQL $Version on port $Port" }
& "$Bin\psql.exe" -h localhost -p $Port -U postgres -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE gas_smoke ENCODING 'UTF8' TEMPLATE template0"
if ($LASTEXITCODE -ne 0) { throw "Could not create smoke-test database" }

$Actual = [int](& "$Bin\psql.exe" -h localhost -p $Port -U postgres -d postgres -tAc "SHOW server_version_num")
if ([math]::Floor($Actual / 10000) -ne $Version) {
    Write-Output "::error::Expected PostgreSQL $Version, server reports $Actual"
    exit 1
}

Add-Content -Path $env:GITHUB_ENV -Value "GAS_TEST_DB_URL=jdbc:postgresql://localhost:$Port/postgres"
Add-Content -Path $env:GITHUB_ENV -Value "GAS_TEST_DB_USER=postgres"
Add-Content -Path $env:GITHUB_ENV -Value "GAS_TEST_DB_PASSWORD=$Password"
Add-Content -Path $env:GITHUB_ENV -Value "GAS_SMOKE_DB_URL=jdbc:postgresql://localhost:$Port/gas_smoke"
Add-Content -Path $env:GITHUB_ENV -Value "GAS_PG_BIN=$Bin"
Add-Content -Path $env:GITHUB_ENV -Value "GAS_PG_PORT=$Port"
Write-Output "PostgreSQL $Version ready on port $Port"
