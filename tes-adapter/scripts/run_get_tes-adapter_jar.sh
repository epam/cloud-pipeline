#!/bin/bash

workdir=$(pwd);

if [ -n "$2" ]; then
    gitRepoUrl="-b $1 $2"
else
    gitRepoUrl=$1
fi

mkdir tmp;
cd tmp;
tmpdir=$(pwd);

#Step 1
echo "Cloning git-repo from $gitRepoUrl"
git clone $gitRepoUrl $tmpdir || { echo "Cloning failed!" >&2; exit 1; };

#Step 2
echo "Building jar-file..."
./gradlew bootJar &> /dev/null || { echo "Building jar-file failed!" >&2; exit 1; };

#Step 3
echo "Copying jar-file from tes-adapter to $workdir"
cp -i tes-adapter/build/libs/tes-adapter-1.0-SNAPSHOT.jar .. || { echo "Copying jar-file failed!" >&2; exit 1; };

#Check exit status and remove tmp files:
echo "Removing tmp directory..."
if [ $? -ne 0 ]; then
	echo "The program failed! See errorlog file"
else
	 rm -r -f $tmpdir; echo "Success!"
fi


