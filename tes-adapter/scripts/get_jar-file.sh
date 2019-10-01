#!/bin/bash

LOGFILE=script.log
# exit when any command fails
set -e
# keep track of the last executed command
trap 'last_command=$current_command; current_command=$BASH_COMMAND' DEBUG
# echo an error message before exiting
trap 'echo "\"${last_command}\" command filed with exit code $?." | tee -a $LOGFILE' ERR

workdir=$(pwd)

if [ -n "$2" ]; then
    gitRepoUrl="-b $1 $2"
else
    gitRepoUrl=$1
fi

echo "Creating tmp directory..."
mkdir tmp
cd tmp
tmpdir=$(pwd)
TES_PATH=$tmpdir/tes-adapter
cd ..

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
    cd tmp
    ./gradlew tes-adapter:bootJar > /dev/null 2>&1
else
   echo "The $TES_PATH directory doesn't exist!" 2>&1; exit 1
fi

# Step 3
echo "Copying jar-file from tes-adapter to $workdir"
cp -rf tes-adapter/build/libs/tes-adapter-1.0-SNAPSHOT.jar ..

# Remove tmp files:
echo "Removing tmp directory..."
rm -r -f $tmpdir

echo "Success! You can find the corresponding jar-file in the folder $workdir"