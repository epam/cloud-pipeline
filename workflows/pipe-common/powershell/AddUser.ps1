param (
    $UserName,
    $UserPassword
)

$SecuredUserPassword = ConvertTo-SecureString -String $UserPassword -AsPlainText -Force
New-LocalUser -Name $UserName -Password $SecuredUserPassword -AccountNeverExpires
Add-LocalGroupMember -Group "Administrators" -Member "$UserName"
