#!/bin/bash

# exit when any command fails
set -e
# keep track of the last executed command
trap 'last_command=$current_command; current_command=$BASH_COMMAND' DEBUG
# echo an error message before exiting
trap 'echo "\"${last_command}\" command filed with exit code $?." cleanup' ERR

# deletes the temp directory
function cleanup {
  rm -rf $tmpdir
  echo "Deleted temp directory $tmpdir"
}

#cleanup before exiting
trap cleanup EXIT

workdir=$(pwd)

if [ -n "$2" ]; then
    gitRepoUrl="-b $1 $2"
else
    gitRepoUrl=$1
fi

echo "Creating tmp directory..."

tmpdir=$(mktemp -d -t cloud_pipeline-XXXXXXXXXX)
TES_PATH=$tmpdir/tes-adapter
TES_JAR_PATH=$TES_PATH/build/libs/tes-adapter-1.0-SNAPSHOT.jar

# Step 1.
# Cloning cloud-pipeline(CP) API from git repository,
# where $1 is URL(with possible specified branch(-b)), and
# $2 is where the CP will be copied.
echo "Cloning git-repo from $gitRepoUrl"
git clone $gitRepoUrl $tmpdir;

# Step 2.
# Build jar file from tes-adapter module(TES-PATH)
echo "Building jar-file..."
if [ -d "$TES_PATH" ]; then
    cd $tmpdir
    ./gradlew tes-adapter:bootJar >/dev/null 2>&1
else
   echo "The $TES_PATH directory doesn't exist!" 2>&1; exit 1
fi

# Step 3
echo "Copying jar-file from tes-adapter to $workdir"
cp -rf $TES_JAR_PATH $workdir

echo "Success! You can find the corresponding jar-file in the folder $workdir"