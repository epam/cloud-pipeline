#!/bin/bash
# Printing help:
python export_runs.py -h

# Common usage:
# python export_runs.py -s <since date> -a <api path> -k <authentication token> -o <output file> [--skip-groups] [--skip-ad-groups]
# <since date> - 'yyyy-MM-dd' or 'yyyy-MM-dd HH:mm:ss'. By default - January,1 of current year
# --skip-groups - (optional, False by default) - do not include user groups columns
# --skip-ad-groups - (optional, False by default) - do not inclide user ad groups columns

API_PATH='https://HOST:PORT/pipeline/restapi'
TOKEN='<JWT_TOKEN>'

# Exporting runs from April, 19, 2018:
python export_runs.py -s 2018-04-19 -a $API_PATH -k $TOKEN -o today.csv

# Exporting current year's runs:
python export_runs.py -a $API_PATH -k $TOKEN -o year.csv