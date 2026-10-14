# [MANUAL] Add descriptions to all parameters within launch form validation

Test verifies the tooltip text shown for each field's **?** icon on the launch form.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Create a pipeline |  |
| 2 | Click the pipeline |  |
| 3 | Click the version |  |
| 4 | Click the **Run** button |  |
| 5 | Click **Exec environment** |  |
| 6 | Click **Advanced** |  |
| 7 | Hover over the **?** icon near **Estimated price per hour** | A tooltip appears:<br>`Price per hour: <value> $`<br>`Minimum price: <value> $`<br>`Maximum price: <value> $`<br>`Average price: <value> $` |
| 8 | Hover over the **?** icon near **Docker image** | A tooltip appears: `This docker image will be used to execute a current job script. Select docker image that provides required environment and set of tools` |
| 9 | Hover over the **?** icon near **Type** | A tooltip appears: `Select type of a calculation instance that will execute current job. Tune type of an instance to fulfill job's hardware requirements in terms of CPUs and Memory. To launch a number of calculation instances - tick "Launch cluster" checkbox and specify number of additional worker nodes. This will run current job on a master node that is connected to the workers via SSH` |
| 10 | Hover over the **?** icon near **Disk (Gb)** | A tooltip appears: `Define disk storage for the selected calculation instance type. This volume will be available for the job within an instance at runtime` |
| 11 | Hover over the **?** icon near **Price type** | A tooltip appears: `Spot type will provide ~3 times lower prices, but may introduce longer startup time and accidental node failure. On-demand type is more expensive but provides solid init and runtime behavior. Use Spot for testing and debugging purposes` |
| 12 | Hover over the **?** icon near **Timeout (min)** | A tooltip appears: `If defined - will terminate job when specified duration in minutes is elapsed` |
| 13 | Hover over the **?** icon near **Cmd template** | A tooltip appears: `Command template is a shell script that will be executed as first process within a calculation instance. It can be treated as an entrypoint for the job. Typically job initialization script shall be called in command template. All values that are specified in the "Parameters" section - will be available within a command template and downstream processes as environment variables. If no specific job is required to run and only SSH access is required - tick "Start idle" checkbox. When a job is started idle - container will be executed with "sleep infinity" command and will be available via SSH` |
