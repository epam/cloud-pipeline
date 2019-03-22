/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

const OS_WINDOWS = 'Windows';
const OS_LINUX = 'Linux';
const OS_MACOS = 'MacOS';
const OS_OTHER = 'Other';

export const OperationSystems = {
  windows: OS_WINDOWS,
  linux: OS_LINUX,
  macOS: OS_MACOS,
  other: OS_OTHER
};

export function getOS () {
  if (navigator.appVersion.indexOf('Win') !== -1) {
    return OperationSystems.windows;
  }
  if (navigator.appVersion.indexOf('Mac') !== -1) {
    return OperationSystems.macOS;
  }
  if (navigator.appVersion.indexOf('Linux') !== -1) {
    return OperationSystems.linux;
  }
  return OperationSystems.other;
}
