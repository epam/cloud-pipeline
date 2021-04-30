param (
    $Status,
    $Text,
    $Task
)

RequestApi -HttpMethod "POST" -ApiMethod "run/$env:RUN_ID/log" -Body @"
{
    "date": "$(Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff")",
    "logText": "$Text",
    "status": "$Status",
    "taskName": "$Task"
}
"@
