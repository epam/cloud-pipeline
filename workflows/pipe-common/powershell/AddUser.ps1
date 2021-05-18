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
    $UserPassword
)

$SecuredUserPassword = ConvertTo-SecureString -String $UserPassword -AsPlainText -Force
New-LocalUser -Name $UserName -Password $SecuredUserPassword -AccountNeverExpires
Add-LocalGroupMember -Group "Administrators" -Member "$UserName"
