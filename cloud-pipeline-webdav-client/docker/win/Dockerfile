# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

FROM node:14.9.0-stretch
RUN dpkg --add-architecture i386
RUN apt-get -yq update \
  && apt-get -yq install software-properties-common=0.96.20.2-1+deb9u1 apt-transport-https=1.4.10 zip=3.0-11+b1 \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/*
RUN wget -nc https://dl.winehq.org/wine-builds/winehq.key \
  && apt-key add winehq.key \
  && apt-add-repository https://dl.winehq.org/wine-builds/debian/ \
  && apt-get -yq update \
  && apt-get -yq install --install-recommends winehq-stable=5.0.2~stretch \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/*
