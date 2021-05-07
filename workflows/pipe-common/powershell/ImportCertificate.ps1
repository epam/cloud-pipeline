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

param(
    $FilePath,
    $CertStoreLocation
)

$ImportOutput = Import-Certificate -FilePath "$FilePath" -CertStoreLocation "$CertStoreLocation"
$ImportExitCode = $LASTEXITCODE
if ("X509Certificate2".equals($ImportOutput.GetType().name)) {
    $CertSubject = $ImportOutput.Subject
    $CertIssuer = $ImportOutput.Issuer
    $CertNotAfter = $ImportOutput.NotAfter -replace "\r\n", ''
    $CertThumbprint = $ImportOutput.Thumbprint
    $CertDetails = "Subject=[$CertSubject]; IssuedBy=[$CertIssuer]; Expires=[$CertNotAfter]; Thumbprint=[$CertThumbprint]"
    if ($ImportExitCode -eq 0) {
        Write-Output "Certificated imported successfully: $CertDetails"
    } else {
        Write-Output "Certificated updated: $CertDetails"
    }
    exit 0
}
Write-Error "Unable to import certificate!"
exit 1
