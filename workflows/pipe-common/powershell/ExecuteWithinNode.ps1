param (
    $Command
)

ssh -o "StrictHostKeyChecking no" -i $env:HOST_ROOT\.ssh\id_rsa "NODEUSER@$env:NODE_IP" powershell -Command "$Command"
