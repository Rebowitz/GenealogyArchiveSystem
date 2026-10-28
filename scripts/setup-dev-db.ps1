# Developer convenience (Windows): creates the local development role and database.
# Needs psql.exe (installed with PostgreSQL, e.g. C:\Program Files\PostgreSQL\16\bin) and the
# 'postgres' superuser password chosen during PostgreSQL installation.
#   .\scripts\setup-dev-db.ps1 [-PsqlPath 'C:\Program Files\PostgreSQL\16\bin\psql.exe']
param(
    [string]$PsqlPath = 'psql.exe',
    [string]$Role = 'genealogy_archive',
    [string]$Database = 'genealogy_archive'
)
$ErrorActionPreference = 'Stop'
$secure = Read-Host "Password for new PostgreSQL role '$Role'" -AsSecureString
$pw = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
$sql = @"
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'role', :'pw') WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'role') \gexec
SELECT format('CREATE DATABASE %I OWNER %I ENCODING ''UTF8'' TEMPLATE template0', :'db', :'role') WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'db') \gexec
"@
$sql | & $PsqlPath -U postgres -v ON_ERROR_STOP=1 -v "role=$Role" -v "db=$Database" -v "pw=$pw"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Output "Role '$Role' and database '$Database' are ready. For integration tests also grant CREATEDB:"
Write-Output "  psql -U postgres -c 'ALTER ROLE $Role CREATEDB'"
