# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

param (
    $DavURL,
    [switch] $ShowNotifications
)

$UserName="<USER_NAME>"
$BearerToken="<USER_TOKEN>"
$TargetDrive = "Z:"

function Try-MountDrive($DavURL, $UserName, $BearerToken) {
    Set-ItemProperty -Path "HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings" -Name WarnonZoneCrossing -Value 0
    $InternetExplorer = New-Object -ComObject internetexplorer.application
    $AuthHeader = "Authorization:$BearerToken"
    $DavSsoUrl = "$DavURL/auth-sso/"
    $InternetExplorer.navigate($DavSsoUrl, $null, $null, $null, $AuthHeader)
    $InternetExplorer.Visible = $false
    $WaitingTimeout = 5
    While ($WaitingTimeout -ge 0 -And $InternetExplorer -ne $null -And $InternetExplorer.Busy ) {
        Start-Sleep -Seconds 1
        $WaitingTimeout--
    }
    Start-Sleep -Seconds 2
    taskkill /F /T /IM iexplore.exe
    $MountResult = $(net use "$TargetDrive" "$DavURL/$UserName/" 2>&1 )
    $MountExitCode = $LASTEXITCODE
    if ($MountExitCode -ne 0) {
        if ($MountResult.length -gt 0) {
            $ExceptionMessage = $MountResult[0].Exception.Message
            $MountExitCode = $ExceptionMessage -replace "[^0-9]" , ''
            if ($MountResult.length -gt 2) {
                $ExceptionDetails = $MountResult[2].Exception.Message
                $ExceptionMessage = "$ExceptionMessage $ExceptionDetails"
            }
            Write-Host $ExceptionMessage
        }
    }
    return $MountExitCode
}

function Show-PopUp($Message, $Header) {
    $infoPopup = New-Object -ComObject Wscript.Shell
    $infoPopup.Popup($Message, 0, $Header, 0x0)
}

$MaxRetries = 30
for ($RetryNum = 1; $RetryNum -le $MaxRetries; $RetryNum++) {
    $MountOutput = Try-MountDrive "$DavURL" "$UserName" "$BearerToken"
    if ("Object[]".equals($MountOutput.gettype().name)) {
        $MountExitCode = $MountOutput[$MountOutput.length-1]
    } else {
        $MountExitCode = MountOutput
    }
    if ($MountExitCode -eq 0) {
        while (!(Test-Path -Path $TargetDrive)) {
            Start-Sleep -Seconds 5
        }
        $WshShell = New-Object -comObject WScript.Shell
        $Shortcut = $WshShell.CreateShortcut("$HOME\Desktop\$UserName WebDAV.lnk")
        $Shortcut.TargetPath = "$TargetDrive\"
        $Shortcut.Save()
        $SuccessMsg = "Storage available for '$UserName' are mounted into $TargetDrive\"
        Write-Host $SuccessMsg
        if ($ShowNotifications) {
            Show-PopUp "$SuccessMsg" "StorageMapping"
        }
        exit 0
    }
    if ($MountExitCode -eq 85) {
        $WarnInUseMsg ="WebDAV mapping: Device $TargetDrive\ is already in use"
        Write-Host $WarnInUseMsg
        if ($ShowNotifications) {
            Show-PopUp "$WarnInUseMsg" "StorageMapping"
        }
        exit 0
    }
    Write-Host "Retrying in 10 seconds..."
    Start-Sleep -Seconds 10
}
$FailMsg = "WebDAV mapping: Drive mapping failed!"
Write-Host $FailMsg
Show-PopUp "$FailMsg" "StorageMapping [ERROR]"
pause
exit 1
