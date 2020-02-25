/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

const INTERSECTION_MARGIN = 1;

export function sectionsIntersects (a, b) {
  const {p1: ap1, p2: ap2} = a;
  const {p1: bp1, p2: bp2} = b;
  return Math.max(ap1, ap2, bp1, bp2) - Math.min(ap1, ap2, bp1, bp2) - 2 * INTERSECTION_MARGIN <
    Math.max(ap1, ap2) - Math.min(ap1, ap2) + Math.max(bp1, bp2) - Math.min(bp1, bp2);
}
