#!/usr/bin/env bash

# Construct notebook http URL, that will match EDGE's notation (e.g. /jupyter-gitlab-4800-8888-0 or /pipeline-4801-8888-0)
export pipeline_name=$(hostname | awk '{print tolower($0)}')
export jupyter_port=${NOTEBOOK_SERVE_PORT:-8888}
export endpoint_number=0
export notebook_dir=${NOTEBOOK_SERVE_DIR:-"/home/${OWNER}"}
export base_url="/$pipeline_name-$jupyter_port-$endpoint_number"

# "disable_check_xsrf" option was added, as "'_xsrf' argument missing from post"
# might be thrown from time to time, see https://stackoverflow.com/questions/55014094/jupyter-notebook-not-saving-xsrf-argument-missing-from-post
jupyter notebook --ip '0.0.0.0' \
                 --port $jupyter_port \
                 --no-browser \
                 --NotebookApp.token='' \
                 --NotebookApp.notebook_dir=$notebook_dir \
                 --NotebookApp.base_url=$base_url \
                 --NotebookApp.disable_check_xsrf=True \
                 --allow-root
