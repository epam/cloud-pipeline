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

export function costMapper (value) {
  if (!value || isNaN(value)) {
    return 0;
  }
  return Math.round(+value / 100.0) / 100.0;
}

export function minutesToHours (minutes) {
  if (!minutes || isNaN(minutes)) {
    return 0;
  }

  return Math.round(((minutes / 60) + Number.EPSILON) * 100) / 100;
}

export function bytesToGbs (bytes) {
  if (!bytes || isNaN(bytes)) {
    return 0;
  }
  const bInGb = 1024 * 1024 * 1024;
  return Math.round(((bytes / bInGb) + Number.EPSILON) * 100) / 100;
}
