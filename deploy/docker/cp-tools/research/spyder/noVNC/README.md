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

This will launch a new `Spyder` run with the default parameter

## Logging into Spyder instance

* Once a docker is launched - await 2-4 minutes for the instance initialization (`Spyder-novnc` run will be marked in yellow)
* Hover run id with a mouse - `Spyder-novnc` GUI endpoint URL will be shown (or click `Spyder-novnc` run and endpoint URL will be shown within a run details form)
* Click `Endpoint URL` - noVNC will load and start connection to the instance in the new browser tab

## Running Spyder using GUI

* Once logged into the instance - `Spyder` shortcut will be available on the current desktop
* Double click it to run `Spyder` application
