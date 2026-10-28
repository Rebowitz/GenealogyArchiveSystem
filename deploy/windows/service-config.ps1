# Pure helpers: dot-sourcing installs nothing.
function ConvertTo-WinSWXml {
    param(
        [Parameter(Mandatory = $true)][string]$Template,
        [Parameter(Mandatory = $true)][string]$JavaExe,
        [Parameter(Mandatory = $true)][string]$AppDir,
        [Parameter(Mandatory = $true)][string]$DataDir
    )
    $document = New-Object System.Xml.XmlDocument
    $document.PreserveWhitespace = $true
    $document.LoadXml($Template)
    foreach ($element in 'executable', 'arguments', 'workingdirectory', 'logpath') {
        $node = $document.SelectSingleNode("/service/$element")
        if ($null -eq $node) { throw "WinSW template is missing $element" }
        $node.InnerText = $node.InnerText.Replace('@JAVA@', $JavaExe).Replace('@APP_DIR@', $AppDir).Replace('@DATA_DIR@', $DataDir)
    }
    return $document.OuterXml
}
function Assert-WinSWIntegrity {
    param([Parameter(Mandatory = $true)][string]$Path)
    $expected = (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'WinSW-x64.exe.sha256') -Raw).Trim()
    if ($expected -notmatch '^[a-f0-9]{64}$') { throw 'Invalid pinned WinSW digest' }
    $actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $expected) { throw 'WinSW SHA-256 mismatch; refusing to execute the service wrapper' }
}
