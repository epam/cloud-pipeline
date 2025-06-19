# Nextflow weblog handler

This python web application allows to listen nextflow weblog plugin events: https://github.com/nextflow-io/nf-weblog
Which can be enabled by running nextflow with parameter `-with-weblog <url>`. 
Where `<url>` is an address of the endpoint where nextflow will send all such events.

### How to use

[nf-weblog-handler.sh](nf-weblog-handler.sh) - Starter script to launch this hanler

1. Run the script to enable Nextflow event handler
    ```commandline
    bash nf-weblog-handler.sh --start --port 8080
    ```
    This command will start the application on port 8080. And events can be submitted to `<app-host>:8080/nextflow/event`

2. Start nextflow run with an additional parameter `-with-weblog <app-host>:8080/nextflow/event`
3. Event handler will consume, batch, and redirect all event to the Cloud-Pipeline `/run/{runId}/engine/tasks` REST API