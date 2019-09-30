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

git clone $gitRepoUrl $tmpdir;

./gradlew bootJar;
echo "Copying jar-file from tes-adapter to $workdir"
cp -i tes-adapter/build/libs/tes-adapter-1.0-SNAPSHOT.jar ..;

rm -r -f $tmpdir;
echo "tmp directory removed"