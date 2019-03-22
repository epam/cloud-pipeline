**Note: This docker image uses noMachine to provide remote desktop access, this requires additional client software installation: [noMachine Download Link](https://www.nomachine.com/download/download&id=16)**

**Note: This docker is CPU-only, to run a CUDA-enabled instance - use library/ubuntu-nomachine-cuda**

# Docker information

This docker image provides lightweight desktop environment within the Cloud infrastructure, including the following components

* `Ubuntu 16.04/18.04` as a base Linux distribution
* `Xfce4` as a Desktop GUI for Ubuntu
* `noMachine` for remote desktop access

# Running this docker image

## Installing noMachine client

* Install NoMachine client (you can skip this step if nomachine is already installed)
  * [Windows client download](https://www.nomachine.com/download/download&id=16)
  * [MacOS client download](https://www.nomachine.com/download/download&id=15)
  * [Linux client download](https://www.nomachine.com/download/download&id=4)

## Running this docker image with the `default` settings

* Click `Run` button in the top-right corner
* Confirm tool run in the popup

This will launch a new run with the default parameters: 4 CPUs and 100Gb disk

## Logging into the instance

* Once a docker is launched - await 2-4 minutes for the instance initialization (`ubuntu-nomachine` run will be marked in yellow)
* Hover run id with a mouse - `ubuntu-nomachine` GUI endpoint URL will be shown (or click `ubuntu-nomachine` run and endpoint URL will be shown within a run details form)
* Click `Endpoint URL` - noMachine configuration file will be downloaded (cloud-service-RUNNO.nxs)
* Double click it and noMachine will load and start connection to the instance
```
Note: for the first time – NoMachine client may ask, whether to trust a cloud instance – click Yes button
```
