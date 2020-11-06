#!/bin/bash

API_STATIC_PATH=api/src/main/resources/static
rm -rf ${API_STATIC_PATH}/*
rm -rf build/install/dist/*


_BUILD_DOCKER_IMAGE="lifescience/cloud-pipeline:python2.7-centos6" ./gradlew -PbuildNumber=${APPVEYOR_BUILD_NUMBER}.${APPVEYOR_REPO_COMMIT} -Pprofile=release pipe-cli:buildLinux --no-daemon -x :pipe-cli:test
mv pipe-cli/dist/dist-file/pipe ${API_STATIC_PATH}/pipe-el6
mv pipe-cli/dist/dist-folder/pipe.tar.gz ${API_STATIC_PATH}/pipe-el6.tar.gz

./gradlew distTar   -PbuildNumber=${APPVEYOR_BUILD_NUMBER}.${APPVEYOR_REPO_COMMIT} \
                    -Pprofile=release \
                    -x test \
                    -Pfast \
                    --no-daemon

if [ "$APPVEYOR_REPO_NAME" == "epam/cloud-pipeline" ]; then
    DIST_TGZ_NAME=$(echo build/install/dist/cloud-pipeline*)

    # Publish repackaged distribution tgz to S3 into builds/ prefix
    # Only if it is one of the allowed branches and it is a push (not PR)
    if [ "$APPVEYOR_REPO_BRANCH" == "develop" ] || [ "$APPVEYOR_REPO_BRANCH" == "master" ] || [[ "$APPVEYOR_REPO_BRANCH" == "release/"* ]] || [[ "$APPVEYOR_REPO_BRANCH" == "stage/"* ]]; then
            aws s3 cp $DIST_TGZ_NAME s3://cloud-pipeline-oss-builds/builds/${APPVEYOR_REPO_BRANCH}/
    fi
fi
