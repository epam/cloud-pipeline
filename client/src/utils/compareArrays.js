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

export default function compareArrays (arr1, arr2, elementComparer) {
  const arrayIsEmpty = (array) => {
    return !array || !array.length;
  };
  if (arrayIsEmpty(arr1) && !arrayIsEmpty(arr2)) {
    return false;
  }
  if (!arrayIsEmpty(arr1) && arrayIsEmpty(arr2)) {
    return false;
  }
  if (arrayIsEmpty(arr1) && arrayIsEmpty(arr2)) {
    return true;
  }
  if (arr1.length !== arr2.length) {
    return false;
  }
  const findEqual = (element, array) => {
    if (elementComparer) {
      for (let j = 0; j < array.length; j++) {
        if (elementComparer(element, array[j])) {
          return array[j];
        }
      }
    } else {
      const index = array.indexOf(element);
      if (index >= 0) {
        return array[index];
      }
    }
    return null;
  };
  for (let i = 0; i < arr1.length; i++) {
    if (!findEqual(arr1[i], arr2)) {
      return false;
    }
  }
  return true;
}
