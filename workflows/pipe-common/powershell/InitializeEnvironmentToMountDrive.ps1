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

$WinRegistryInternetSettingsPath = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings"
$WinRegistryTrustedSitesPath = "$WinRegistryInternetSettingsPath\ZoneMap\EscDomains"
New-Item -Path "$WinRegistryTrustedSitesPath" -Name "cluster.local"
New-Item -Path "$WinRegistryTrustedSitesPath\cluster.local" -Name "cp-edge.default.svc"
New-ItemProperty -Path "$WinRegistryTrustedSitesPath\cluster.local\cp-edge.default.svc" -Name https -PropertyType DWORD -Value 2
New-Item -Path "$WinRegistryTrustedSitesPath" -Name "internet"
New-ItemProperty -Path "$WinRegistryTrustedSitesPath\internet" -Name about -PropertyType DWORD -Value 2
New-ItemProperty -Path "HKCU:\Software\Microsoft\Internet Explorer\Main" -Name DisableFirstRunCustomize -PropertyType DWORD -Value 1
Set-ItemProperty -Path "$WinRegistryInternetSettingsPath" -Name WarnonZoneCrossing -Value 0

Set-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Services\WebClient\Parameters" -Name FileSizeLimitInBytes -Value $([uint32]::MaxValue)
Restart-Service -Name WebClient
