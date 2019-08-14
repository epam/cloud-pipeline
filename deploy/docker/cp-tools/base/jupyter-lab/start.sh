#!/usr/bin/env bash

# Parse options
POSITIONAL=()
while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in
        -l|--lab)
        jupyter_type="lab"
        shift
        ;;
        -l|--latest)
        jupyter_checkout_git="yes"
        shift
        ;;
        -s|--notebook-dir)
        jupyter_serve_dir="$2"
        shift
        shift
        ;;
        *)    # unknown option
        POSITIONAL+=("$1") # save it in an array for later
        shift # past argument
        ;;
    esac
done
set -- "${POSITIONAL[@]}"

# Determine if this is a "pipeline" or a "standalone" run
# - "pipeline": default notebook directory will be set to the cloned git repo
# - "standalone": default notebook directory will be set to user's home directory
# - These default values can be overriden by NOTEBOOK_SERVE_DIR variable or --notebook-dir option
if [ "$PIPELINE_NAME" ] && [ "$PIPELINE_NAME" != "pipeline" ]; then
    jupyter_default_serve_dir="$SCRIPTS_DIR/src"
else
    jupyter_default_serve_dir="/home/$OWNER"
fi

# If --lab is not specified - use "notebook" by default
[ -z "$jupyter_type" ] && jupyter_type="notebook"

# If NOTEBOOK_SERVE_DIR is not defined - default directory for a current run type will be used (see comments above)
[ -z "$jupyter_serve_dir" ] &&  jupyter_serve_dir="${NOTEBOOK_SERVE_DIR:-$jupyter_default_serve_dir}"

# Construct notebook http URL, that will match EDGE's notation (e.g. /jupyter-gitlab-4800-8888-0 or /pipeline-4801-8888-0)
export pipeline_name=$(hostname | awk '{print tolower($0)}')
export jupyter_port=${NOTEBOOK_SERVE_PORT:-8888}
export endpoint_number=0
export notebook_dir=${jupyter_serve_dir}
export base_url="/$pipeline_name-$jupyter_port-$endpoint_number"

# If --latest is specified - we are considering notebook directory as a git repository and will try to checkout latest commit for a current branch
if [ "$jupyter_checkout_git" ]; then
    if [ -d "$notebook_dir" ]; then
        cd $notebook_dir
        if ! git rev-parse --git-dir > /dev/null 2>&1; then
            echo "Directory $notebook_dir is not a git repository, cannot checkout master branch"
        else
            git checkout master
        fi
    else
        echo "$notebook_dir does not exist, cannot checkout master branch"
    fi
fi


jupyter $jupyter_type --ip '0.0.0.0' \
                    --port $jupyter_port \
                    --no-browser \
                    --NotebookApp.token='' \
                    --NotebookApp.notebook_dir=$notebook_dir \
                    --NotebookApp.base_url=$base_url \
                    --allow-root
