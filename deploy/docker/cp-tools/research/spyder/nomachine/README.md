# Anaconda
The open source Anaconda Distribution is the fastest and easiest way to do Python and R data science and machine learning on Linux, Windows, and Mac OS X. It's the industry standard for developing, testing, and training on a single machine.

# Spyder
Spyder is a powerful scientific environment written in Python, for Python, and designed by and for scientists, engineers and data analysts. It offers a unique combination of the advanced editing, analysis, debugging, and profiling functionality of a comprehensive development tool with the data exploration, interactive execution, deep inspection, and beautiful visualization capabilities of a scientific package.

# Docker information

This docker image contains Python 3 runtime (managed by Anaconda) and Spyder IDE

* `Anaconda3 5.3.1`
* `Python 3.6`
* `Spyder 3.2.2`
* `noVNC` for remote desktop access

# Running Spyder docker image

## Running this docker image with the `default` settings

* Click `Run` button in the top-right corner
* Confirm tool run in the popup
* Install [NoMachine client](https://www.nomachine.com/download/download&id=16) (you can skip this step if nomachine is already installed)

This will launch a new `Spyder` run with the default parameters

## Logging into Spyder instance

* Once a tool is launched - await 5-7 minutes for the instance initialization (`Spyder` run will be marked in yellow)
* Hover run id with a mouse - `Spyder` GUI endpoint URL will be shown (or click `Spyder` run and endpoint URL will be shown within a run details form)
* Click `Endpoint URL` - noMachine configuration file will be downloaded (cloud-service-RUNNO.nxs)
* Double click it and noMachine will load and start connection to the `Spyder` instance
```
Note: for the first time – NoMachine client may ask, whether to trust a cloud instance – click Yes button
```

## Running Spyder using GUI

* Once logged into the instance - `Spyder` shortcut will be available on the current desktop
* Double click it to run `Spyder` application
