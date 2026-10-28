# Native Windows, no installation/admin privileges/Pester required.
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '..\..\deploy\windows\service-config.ps1')
$count = 0
foreach ($name in 'GenealogyArchive-Web', 'GenealogyArchive-Worker') {
    $template = Get-Content -LiteralPath (Join-Path $PSScriptRoot "..\..\deploy\windows\$name.xml") -Raw
    foreach ($suffix in @(" & Family's", ' & < > " '' synthetic text')) {
        # The second case tests all XML metacharacters, not filesystem path validity.
        $app = "D:\Archive$suffix\app"
        $data = "D:\Archive$suffix\data"
        $java = "C:\Java$suffix\bin\java.exe"
        $rendered = ConvertTo-WinSWXml -Template $template -JavaExe $java -AppDir $app -DataDir $data
        [xml]$parsed = $rendered
        if ($parsed.service.executable -cne $java) { throw 'Executable did not round trip' }
        if ($parsed.service.workingdirectory -cne $data) { throw 'Data directory did not round trip' }
        if ($parsed.service.logpath -cne "$data\logs\service") { throw 'Log path did not round trip' }
        $command = if ($name -eq 'GenealogyArchive-Web') { 'serve' } else { 'worker' }
        $want = '-jar "{0}\genealogy-archive.jar" {1} --config="{2}\installation.yml"' -f $app, $command, $data
        if ($parsed.service.arguments -cne $want) { throw 'Arguments did not round trip' }
        $count++
    }
}
$fake = Join-Path ([IO.Path]::GetTempPath()) ("gas-winsw-digest-" + [guid]::NewGuid() + '.exe')
try {
    Set-Content -LiteralPath $fake -Value 'not the release artifact'
    $rejected = $false
    try { Assert-WinSWIntegrity -Path $fake } catch {
        if ($_.Exception.Message -notmatch 'SHA-256 mismatch') { throw }
        $rejected = $true
    }
    if (-not $rejected) { throw 'Tampered wrapper accepted' }
    $count++
} finally {
    Remove-Item -LiteralPath $fake
}
Write-Output "SERVICE CONFIG REGRESSIONS PASSED: $count checks"
