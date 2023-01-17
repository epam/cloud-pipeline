/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

function getVec4 (aValue) {
  let x = 1.0;
  let y = 1.0;
  let z = 1.0;
  const w = 1.0;
  if (typeof aValue === 'number') {
    x = y = z = aValue;
  } else if (aValue && Array.isArray(aValue)) {
    [x = 1, y = 1, z = 1] = aValue;
  }
  return [x, y, z, w];
}

export function mat4identity (value = 1.0) {
  const [x, y, z, w] = getVec4(value);
  return new Float32Array([
    x, 0, 0, 0,
    0, y, 0, 0,
    0, 0, z, 0,
    0, 0, 0, w
  ]);
}

export function mat4scale (scale) {
  return mat4identity(scale);
}

export function mat4translate (x, y, z) {
  let xx = x || 0;
  let yy = y || 0;
  let zz = z || 0;
  return new Float32Array([
    1, 0, 0, 0,
    0, 1, 0, 0,
    0, 0, 1, 0,
    xx, yy, zz, 1
  ]);
}
