**Note: This docker shall be used for GPU/CUDA-enabled workloads, to run a CPU-only instance - use library/centos-novnc**

# Docker information

This docker image provides lightweight desktop environment within the Cloud infrastructure, including the following components

* `Centos 7` as a base Linux distribution
* `Xfce4` as a Desktop GUI for Centos
* `noVNC` for remote desktop access
* `CUDA 11` toolkit

# Running this docker image

## Running this docker image with the `default` settings

* Click `Run` button in the top-right corner
* Confirm tool run in the popup

This will launch a new run with the default parameters: 4 CPUs and 100Gb disk

## Logging into the instance

* Once a docker is launched - await 2-4 minutes for the instance initialization (`centos-novnc-cuda` run will be marked in yellow)
* Hover run id with a mouse - `centos-novnc-cuda` GUI endpoint URL will be shown (or click `centos-novnc-cuda` run and endpoint URL will be shown within a run details form)
* Click `Endpoint URL` - noVNC will load and start connection to the instance in the new browser tab
