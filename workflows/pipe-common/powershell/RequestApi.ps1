param(
    $HttpMethod,
    $ApiMethod,
    $Body = $null
)

$Response = Invoke-RestMethod -Method $HttpMethod `
                              -Uri "${env:API}${ApiMethod}" `
                              -Body $Body `
                              -Headers @{
                                  "Authorization" = "Bearer $env:API_TOKEN"
                                  "Content-Type" = "application/json"
                              }

if ($Response.status -ne "OK") {
    Write-Error $Response.message
    return $null
}

return $Response.payload
