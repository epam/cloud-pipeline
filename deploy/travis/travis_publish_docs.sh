#!/bin/bash

# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -e 

# Publish docs only if it is one of the allowed branches and it is a push (not PR)
if [ "$TRAVIS_REPO_SLUG" != "epam/cloud-pipeline" ] || \
    [ "$TRAVIS_BRANCH" != "develop" ] || \
    [ "$TRAVIS_EVENT_TYPE" != "push" ]; then
    echo "Skipping docs publishing, as a build is not triggered from the correct repo/branch via push"
    exit 0
fi

# Build docs - it will produce a tar.gz file in ./dist/
echo "Building docs"
./gradlew -PbuildNumber=${TRAVIS_BUILD_NUMBER}.${TRAVIS_COMMIT} -Pprofile=release buildDoc --no-daemon
mkdir -p $HOME/cloud-pipeline-docs
tar -zxf dist/cloud-pipeline-docs.tar.gz -C $HOME/cloud-pipeline-docs

# Setup git with "travis" committer name
cd $HOME
git config --global user.email "travis@travis-ci.org"
git config --global user.name "Travis"

# Clone gh-pages and replace with new docs
echo "Preparing gh-pages branch"
git clone --quiet --branch=gh-pages https://${GITHUB_TOKEN}@github.com/epam/cloud-pipeline.git gh-pages > /dev/null
cd gh-pages
find . -maxdepth 1 ! -name '.git' ! -name '.' ! -name '..' -exec rm -rf {} +
cp -Rf $HOME/cloud-pipeline-docs/* ./

# Add files, commit and force push
echo "Pushing gh-pages branch"
git add -f .
git commit -m "Docs update via Travis Build $TRAVIS_BUILD_NUMBER"
git push -fq origin gh-pages > /dev/null

echo "Done publishing docs"
