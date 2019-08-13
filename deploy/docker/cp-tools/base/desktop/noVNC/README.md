# Docker information

This docker image provides lightweight desktop environment within the Cloud infrastructure, including the following components

* `Ubuntu 16.04/18.04` as a base Linux distribution
* `Xfce4` as a Desktop GUI for Ubuntu
* `noVNC` for remote desktop access

# Running this docker image

## Running this docker image with the `default` settings

* Click `Run` button in the top-right corner
* Confirm tool run in the popup

This will launch a new run with the default parameters: 4 CPUs and 100Gb disk

## Logging into the instance

* Once a docker is launched - await 2-4 minutes for the instance initialization (`ubuntu-novnc` run will be marked in yellow)
* Hover run id with a mouse - `ubuntu-novnc` GUI endpoint URL will be shown (or click `ubuntu-novnc` run and endpoint URL will be shown within a run details form)
* Click `Endpoint URL` - noVNC will load and start connection to the instance in the new browser tab
