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

function Get-ServiceId {
    param([Parameter(Mandatory = $true)][string]$Name)
    # The WinSW wrapper name maps to the service id in its XML (GenealogyArchive-Web -> GenealogyArchiveWeb).
    return $Name.Replace('-', '')
}
function Invoke-Icacls {
    param([Parameter(Mandatory = $true)][string]$Path, [Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    & icacls $Path @Arguments | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "icacls failed for $Path ($LASTEXITCODE): $($Arguments -join ' ')" }
}
function Set-VirtualServiceAccount {
    # ADR-0010: each service runs as its own virtual account with an unrestricted service SID,
    # so file permissions can be granted to exactly one service.
    param([Parameter(Mandatory = $true)][string]$ServiceId)
    & sc.exe config $ServiceId obj= "NT SERVICE\$ServiceId" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not set the virtual service account for $ServiceId ($LASTEXITCODE)" }
    & sc.exe sidtype $ServiceId unrestricted | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not set the service SID type for $ServiceId ($LASTEXITCODE)" }
}
function Get-ServiceSid {
    param([Parameter(Mandatory = $true)][string]$ServiceId)
    $account = New-Object System.Security.Principal.NTAccount("NT SERVICE", $ServiceId)
    return $account.Translate([System.Security.Principal.SecurityIdentifier]).Value
}
