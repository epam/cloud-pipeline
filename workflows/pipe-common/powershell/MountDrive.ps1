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

$UserName="<USER_NAME>"
$BearerToken="<USER_TOKEN>"

function Try-MountDrive {
    param (
        $UserName,
        $BearerToken
    )

    $DavURL = "https://<EDGE_HOST>:<EDGE_PORT>/webdav"

    $InternetExplorer = New-Object -ComObject internetexplorer.application
    $AuthHeader = "Authorization:$BearerToken"
    $DavSsoUrl = "$DavURL/auth-sso/"
    $InternetExplorer.navigate($DavSsoUrl, $null, $null, $null, $AuthHeader)
    $WaitingTimeout = 10
    While ($WaitingTimeout -ge 0 -And $InternetExplorer -ne $null -And $InternetExplorer.Busy ) {
        Start-Sleep -Seconds 1
        $WaitingTimeout--
    }
    taskkill /F /T /IM iexplore.exe
    $MountResult = $(net use Z: "$DavURL/$UserName/" 2>&1 )
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

$MaxRetries = 3
for ($RetryNum = 1; $RetryNum -le $MaxRetries; $RetryNum++) {
    $MountOutput = Try-MountDrive "$UserName" "$BearerToken"
    if ("Object[]".equals($MountOutput.gettype().name)) {
        $MountExitCode = $MountOutput[$MountOutput.length-1]
    } else {
        $MountExitCode = MountOutput
    }
    if ($MountExitCode -eq 0) {
        Write-Host "Storage available for '$UserName' are mounted into Z:\"
        exit 0
    }
    if ($MountExitCode -eq 85) {
        Write-Host "WebDAV mapping: Device Z:\ is already in use"
        exit 0
    }
    Write-Host "Retrying in 10 seconds..."
    Start-Sleep -Seconds 10
}
Write-Host "WebDAV mapping: Drive mapping failed!"
pause
exit 1
