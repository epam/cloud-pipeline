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

const postfixes = ['',
  'KiB',
  'MiB',
  'GiB',
  'TiB',
  'PiB',
  'EiB'
];

function valuePresentation (value) {
  let index = 0;
  while (value >= 1024 && index < postfixes.length - 1) {
    value /= 1024;
    index += 1;
  }
  return `${value.toFixed(2)} ${postfixes[index]}`;
}

export default function (value, total) {
  const valueDescription = valuePresentation(value);
  const totalDescription = valuePresentation(total);
  const percent = value / total * 100.0;
  return `${valueDescription} of ${totalDescription} (${percent.toFixed(2)}%)`;
};
