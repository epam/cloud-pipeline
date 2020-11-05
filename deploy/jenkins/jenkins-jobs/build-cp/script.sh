#!/bin/bash

if [ -z "$CP_GIT_REF" ]; then
    echo "Version to build is not specified"
    exit 1
fi

rm -rf cloud-pipeline && \
git clone https://github.com/epam/cloud-pipeline && \
cd cloud-pipeline && \
git checkout $CP_GIT_REF

[ $? -ne 0 ] && exit 1

export CP_COMMIT=$(git rev-parse HEAD)
export CP_BUILD_NUMBER=$BUILD_NUMBER

echo
echo BUILDING CLOUD PIPELINE:
echo Commit: $CP_COMMIT
echo Build: $CP_BUILD_NUMBER
echo Ref: $CP_GIT_REF
echo
bash deploy/jenkins/jenkins-jobs/build-cp/build-cp.sh
