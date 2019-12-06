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

const sizePostfix = ['bytes', 'Kb', 'Mb', 'Gb', 'Tb', 'Pb', 'Eb', 'Zb', 'Yb'];

const displaySize = (size, digits = true) => {
  if (isNaN(size)) {
    return size;
  }
  let sizeValue = +size;
  let index = 0;
  while (sizeValue > 1024 && index < sizePostfix.length - 1) {
    index += 1;
    sizeValue /= 1024;
  }
  if (index === 0) {
    return `${sizeValue} ${sizePostfix[index]}`;
  }
  if (digits) {
    return `${sizeValue.toFixed(2)} ${sizePostfix[index]}`;
  }
  return `${Math.round(sizeValue)} ${sizePostfix[index]}`;
};

export default displaySize;
