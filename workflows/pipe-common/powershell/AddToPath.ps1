param (
    $AppendingPath
)

$ProfileDir = $(Split-Path -Path $Profile)

if (-not(Test-Path $ProfileDir)) {
    New-Item -Path $ProfileDir -ItemType "Directory" -Force
}

@"
`$env:PATH = "`$env:PATH;$AppendingPath"
"@ | Out-File -FilePath $Profile -Append -Encoding ascii -Force

$env:Path = "$env:Path;$AppendingPath"
