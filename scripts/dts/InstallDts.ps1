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

$env:API = "{PUT REST API URL HERE}"
$env:API_TOKEN = "{PUT REST API TOKEN HERE}"
$env:API_PUBLIC_KEY = "{PUT REST API PUBLIC KEY}"
$env:DISTRIBUTION_URL = "$env:API" -replace "/restapi/",""
$env:DTS_DIR = "$env:ProgramFiles\CloudPipeline\DTS"
$env:DTS_NAME = hostname

if (-not(Test-Path "$env:DTS_DIR")) { New-Item -Path "$env:DTS_DIR" -ItemType "Directory" -Force }
if (-not(Test-Path "$env:DTS_DIR\logs")) { New-Item -Path "$env:DTS_DIR\logs" -ItemType "Directory" -Force }
if (-not(Test-Path "$env:DTS_DIR\logs\launcher.log")) { New-Item -Path "$env:DTS_DIR\logs\launcher.log" -Force }
if (-not(Test-Path "$env:DTS_DIR\locks")) { New-Item -Path "$env:DTS_DIR\locks" -ItemType "Directory" -Force }

Start-Transcript -Path "$env:DTS_DIR\logs\installer.log" -Append

Write-Host "Installing Data Transfer Service on $env:DTS_NAME host..."
Invoke-WebRequest "$env:DISTRIBUTION_URL/DeployDts.ps1" -OutFile "$env:DTS_DIR\DeployDts.ps1"
& "$env:DTS_DIR\DeployDts.ps1" -Install

Write-Host "Waiting for Data Transfer Service to become ready on $env:DTS_NAME host..."
$duration=0
while($duration -lt 900) {
    Get-Content "$env:DTS_DIR\logs\launcher.log" | Select-String "Launching data transfer service..." | ForEach-Object { break }
    Start-Sleep -Seconds 10
    $duration+=10
}
if ($duration -lt 900) {
    Write-Host "Data Transfer Service $env:DTS_NAME is ready after $duration seconds"
} else {
    Write-Host "Data Transfer Service $env:DTS_NAME is not ready after $duration seconds"
}
Stop-Transcript
