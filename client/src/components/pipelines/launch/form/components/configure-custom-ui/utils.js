/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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

export function checkFormChanges (configurations = [], initial = []) {
  if (configurations.length !== initial.length) {
    return true;
  }
  const checkStringValue = (a, b) => a !== b;
  const checkUserRoles = (userRolesA = [], userRolesB = []) => {
    if (userRolesA.length !== userRolesB.length) {
      return true;
    }
    return userRolesA.some((a, index) => {
      const aName = a.name;
      const bName = userRolesB[index]?.name;
      return checkStringValue(aName, bName);
    });
  };
  return configurations.some((config, index) => {
    if (config.markAsDeleted) {
      return true;
    }
    console.log({
      a: config.userRoles,
      b: initial[index]?.userRoles
    });
    return checkStringValue(config.basePage, initial[index]?.basePage) ||
      checkStringValue(config.customUI, initial[index]?.customUI) ||
      checkUserRoles(config.userRoles, initial[index]?.userRoles);
  });
}
