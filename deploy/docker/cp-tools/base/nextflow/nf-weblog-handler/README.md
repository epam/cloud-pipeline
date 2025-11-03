# Nextflow weblog handler

This python web application allows to listen to Nextflow weblog plugin events: https://github.com/nextflow-io/nf-weblog
It can be enabled in the Nextflow configuration by specifying the URL to which HTTP POST requests (carrying such events) will be sent.

### How to use

[nf-weblog-handler.sh](nf-weblog-handler.sh) - Starter script to launch this hanler

1. Run the script to enable Nextflow event handler
    ```commandline
    bash nf-weblog-handler.sh --start --port 8080
    ```
    This command will start the application on port 8080. And events can be submitted to `<app-host>:8080/nextflow/event`

2. Add the following to `$NXF_HOME/config` (or project `nextflow.config`) to enable the weblog feature in the Nextflow configuration:
```groovy
weblog.enabled = true
weblog.url = 'http://<app-host>:8080/nextflow/event'
```
3. Start the Nextflow run:
```bash
  nextflow run <path-to-nf-file>
```
4. Event handler will consume, batch, and redirect all event to the Cloud-Pipeline `/run/{runId}/engine/tasks` REST API