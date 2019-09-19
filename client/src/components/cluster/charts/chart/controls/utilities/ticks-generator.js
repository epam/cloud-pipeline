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

function getBase (min, max, initialBase) {
  const range = max - min;
  let base = Math.floor(Math.log10(range) / Math.log10(initialBase));
  if (range / (initialBase ** base) < 2) {
    base -= 1;
  } else if (range / (initialBase ** base) > initialBase) {
    base += 1;
  }
  return base;
}

export default function generateTicks (min, max, maxSteps = 10, initialBase = 10) {
  const base = getBase(min, max, initialBase);
  let step = initialBase ** base;
  const result = [];
  const closestMin = Math.ceil(min / step) * step;
  const closestMax = Math.floor(max / step) * step;
  let steps = Math.floor((closestMax - closestMin) / step);
  const half = step / 2.0;
  while (steps > maxSteps) {
    step += half;
    steps = Math.floor((closestMax - closestMin) / step);
  }
  if (closestMin - min > step / 2.0) {
    result.push(min);
  }
  result.push(closestMin);
  let last = closestMin;
  while (last < closestMax) {
    last += step;
    result.push(last);
  }
  if (max - last > step / 2.0) {
    result.push(max);
  }
  return {ticks: result, base};
};
