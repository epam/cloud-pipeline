**Note: This docker shall be used for GPU/CUDA-enabled workloads, to run a CPU-only instance - use library/jupyter-lab**

# Docker information

Basic `Python` + `R` development environment with JupyterLab Web GUI

The following software packages are installed:
* `Miniconda 2/3` (see corresponding version of this image in the `Versions` tab)
* `JupyterLab`
* `Python 2/3 kernels`
* `R kernel`

This image can be run on its own or can be used as a base image for custom environments.

# Running JupyterLab

To run this tool perform the following steps:
1. Click `Run` button in the top-right corner and agree on a tool launch
2. Wait for the container to be initialized (`InitializeEnvironment` task shall be marked as finished)
3. Click `Endpoint` link to navigate to the JupyterLab Web GUI

# Accessing data from JupyterLab instance

Once JupyterLab started and initialized - the following data locations are made available:
1. Buckets (i.e. DataStorage) that are available to user - will be mounted to `~/cloud-data/{BUCKET_NAME}`
2. CloudPipeline CLI is installed and configured automatically - `pipe storage ls/cp/mv` commands can be run to manage data within DataStorages
3. `wget` and `curl` commands can be used as well to get data from the external resources
