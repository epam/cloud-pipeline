param (
    $AppendingPath
)

$RegPath = "HKLM:\System\CurrentControlSet\Control\Session Manager\Environment"
$OldPath = Get-ItemProperty $RegPath "Path" | ForEach-Object { $_.Path }
$NewPath = "$OldPath;$AppendingPath"
Set-ItemProperty $RegPath "Path" -Value $NewPath -type ExpandString
$env:Path = "$env:Path;$AppendingPath"
