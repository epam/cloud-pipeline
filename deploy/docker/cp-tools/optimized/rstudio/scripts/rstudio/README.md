# RStudio base image

Basic `R` development environment with RStudio Web GUI

The following utilities are installed:
* `R v3.4.2`
* `RStudio v1.1.383`

This image can be run on its own or can be used as a base image for custom environments.

# Running RStudio

To run this tool perform the following steps:
1. Click `Run` button in the top-right corner and agree on a tool launch
2. Wait for the container to be initialized (`InitializeEnvironment` task shall be marked as finished)
3. Click `Endpoint` link to navigate to the RStudio Web GUI

# Accessing data from RStudio instance

Once RStudio started and initialized - the following data locations are made available:
1. Buckets (i.e. DataStorage) that are available to user - will be mounted to `~/cloud-data/{BUCKET_NAME}`
2. CloudPipeline CLI is installed and configured automatically - `pipe storage ls/cp/mv` commands can be run to manage data within DataStorages
3. `wget` and `curl` commands can be used as well to get data from the external resources
4. Data can be uploaded using RStudio Web GUI (`Files -> Upload`)
