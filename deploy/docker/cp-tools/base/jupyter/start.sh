#!/usr/bin/env bash

# Construct notebook http URL, that will match EDGE's notation (e.g. /jupyter-gitlab-4800-8888-0 or /pipeline-4801-8888-0)
export pipeline_name=$(hostname | awk '{print tolower($0)}')
export jupyter_port=${NOTEBOOK_SERVE_PORT:-8888}
export endpoint_number=0
export notebook_dir=${NOTEBOOK_SERVE_DIR:-"/home/${OWNER}"}
export base_url="/$pipeline_name-$jupyter_port-$endpoint_number"

export PYSPARK_DRIVER_PYTHON=jupyter
export PYSPARK_DRIVER_PYTHON_OPTS="notebook --ip 0.0.0.0 --port $jupyter_port --no-browser --NotebookApp.token= --NotebookApp.notebook_dir=$notebook_dir --NotebookApp.base_url=$base_url --NotebookApp.disable_check_xsrf=True --allow-root"

export CP_CAP_SPARK_HOST="${CP_CAP_SPARK_HOST:-$(hostname)}"
export CP_CAP_SPARK_PORT="${CP_CAP_SPARK_PORT:-7077}"

spark_master_url="spark://${CP_CAP_SPARK_HOST}:${CP_CAP_SPARK_PORT}"

pyspark --packages io.projectglow:glow-spark3_2.12:1.2.1,io.delta:delta-core_2.12:2.1.0 \
--conf spark.hadoop.io.compression.codecs=io.projectglow.sql.util.BGZFCodec \
--conf "spark.sql.extensions=io.delta.sql.DeltaSparkSessionExtension" \
--conf "spark.sql.catalog.spark_catalog=org.apache.spark.sql.delta.catalog.DeltaCatalog" \
--master "$spark_master_url" \

