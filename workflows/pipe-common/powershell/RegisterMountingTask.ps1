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
    $UserName,
    $BearerToken,
    $EdgeHost,
    $EdgePort,
    $MountingScript
)

function SetPlaceholderInFile {
    param(
        $Placeholder,
        $Replacement,
        $TargetFile
    )
    ((Get-Content -path $TargetFile -Raw) -replace "$Placeholder", "$Replacement") | Set-Content -Path $TargetFile
}

SetPlaceholderInFile -Placeholder "<USER_NAME>" -Replacement $UserName -TargetFile $MountingScript
SetPlaceholderInFile -Placeholder "<USER_TOKEN>" -Replacement $BearerToken -TargetFile $MountingScript
SetPlaceholderInFile -Placeholder "<EDGE_HOST>" -Replacement $EdgeHost -TargetFile $MountingScript
SetPlaceholderInFile -Placeholder "<EDGE_PORT>" -Replacement $EdgePort -TargetFile $MountingScript
SCHTASKS /CREATE /SC ONLOGON /TN "MountCloudPipileneDav" /TR "powershell.exe -windowstyle hidden $MountingScript"
SCHTASKS /RUN /TN "MountCloudPipileneDav"
exit $LASTEXITCODE
