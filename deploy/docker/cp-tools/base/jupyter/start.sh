#!/usr/bin/env bash

. $ANACONDA_HOME/etc/profile.d/conda.sh
conda activate base


# If a "standalone" run is used (i.e. not a "Pipeline") - then use "pipeline" keyword instead of the real pipeline name
export pipeline_name=${PIPELINE_NAME:-pipeline}
pipeline_name=$(echo "$pipeline_name" | awk '{print tolower($0)}')
export jupyter_port=${NOTEBOOK_SERVE_PORT:-8888}
export endpoint_number=0
export notebook_dir=${NOTEBOOK_SERVE_DIR:-"/home/${OWNER}"}
export base_url="/$pipeline_name-$RUN_ID-$jupyter_port-$endpoint_number"

jupyter notebook --ip '0.0.0.0' \
                 --port $jupyter_port \
                 --no-browser \
                 --NotebookApp.token='' \
                 --NotebookApp.notebook_dir=$notebook_dir \
                 --NotebookApp.base_url=$base_url \
                 --allow-root
