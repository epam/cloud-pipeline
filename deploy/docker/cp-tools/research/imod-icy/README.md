# Icy
Icy An open community platform for bioimage informatics.

# IMOD
IMOD is a set of image processing, modeling and display programs used for tomographic reconstruction and for 3D reconstruction of EM serial sections and optical sections. The package contains tools for assembling and aligning data within multiple types and sizes of image stacks, viewing 3-D data from any orientation, and modeling and display of the image files. IMOD was developed primarily by David Mastronarde, Rick Gaudette, Sue Held, Jim Kremer, Quanren Xiong, Suraj Khochare, and John Heumann at the University of Colorado.

# Docker information

This docker image contains Python 3 runtime (managed by Anaconda) and Spyder IDE

* `Icy 2.4.0.0`
* `Imod 4.11.12 CUDA8.0`
* `noMachine` for remote desktop access

## Running this docker image with the `default` settings

* Click `Run` button in the top-right corner
* Confirm tool run in the popup
* Install [NoMachine client](https://www.nomachine.com/download/download&id=16) (you can skip this step if nomachine is already installed)

This will launch a new run with the default parameters

## Logging into instance

* Once a tool is launched - await 5-7 minutes for the instance initialization (run will be marked in yellow)
* Hover run id with a mouse - GUI endpoint URL will be shown (or click run and endpoint URL will be shown within a run details form)
* Click `Endpoint URL` - noMachine configuration file will be downloaded (cloud-service-RUNNO.nxs)
* Double click it and noMachine will load and start connection to the instance
```
Note: for the first time – NoMachine client may ask, whether to trust a cloud instance – click Yes button
```

## Running icy or imod using GUI

* Once logged into the instance - `icy` and `imod` shortcuts will be available on the current desktop
* Other `imod` utilities available with command line
