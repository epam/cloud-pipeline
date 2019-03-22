#!/usr/bin/env bash

pipe_log SUCCESS "Running shell pipeline" "Task1"

cp -r ${INPUT_DIR}/* ${ANALYSIS_DIR}
cp -r ${COMMON_DIR}/* ${ANALYSIS_DIR}