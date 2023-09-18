/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

function getAWSInstanceFamily (instanceType) {
  const e = /^(\w+)\./.exec(instanceType);
  if (e && e[1]) {
    return e[1];
  }
  return undefined;
}

function getGCPInstanceFamily (instanceType) {
  const e = /^(?!custom)(\w+-(?!custom)\w+)-?.*/.exec(instanceType);
  if (e && e[1]) {
    return e[1];
  }
  return undefined;
}

function getAzureInstanceFamily (instanceType) {
  const e = /^([a-zA-Z]+)\d+(.*)/.exec(instanceType);
  if (e && e[1] && e[2]) {
    return e[1].concat(e[2]);
  }
  return undefined;
}

export function getInstanceFamilyByName (instanceName, provider) {
  if (!instanceName || !provider) {
    return undefined;
  }
  if (/^aws$/i.test(provider)) {
    return getAWSInstanceFamily(instanceName);
  }
  if (/^gcp$/i.test(provider)) {
    return getGCPInstanceFamily(instanceName);
  }
  if (/^azure$/i.test(provider)) {
    return getAzureInstanceFamily(instanceName);
  }
  return undefined;
}

export function getInstanceFamily (instance, provider) {
  if (!instance) {
    return undefined;
  }
  const {
    name
  } = instance;
  if (!name) {
    return undefined;
  }
  return getInstanceFamilyByName(name, provider);
}
