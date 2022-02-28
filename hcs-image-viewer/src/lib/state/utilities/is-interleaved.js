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

// source: https://github.com/hms-dbmi/viv/blob/master/avivator/src/utils.js
/**
 * @property {number[]} shape loader shape
 */
export default function isInterleaved(shape) {
  const lastDimSize = shape[shape.length - 1];
  return lastDimSize === 3 || lastDimSize === 4;
}
