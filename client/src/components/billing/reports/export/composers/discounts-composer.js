/*
 *
 *  * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

function compose (csv, discounts) {
  return new Promise((resolve) => {
    const {
      computeValue = 0,
      storageValue = 0
    } = discounts || {};
    const format = a => `${Math.round(a * 100.0) / 100.0} %`
      .replace(new RegExp(csv.SEPARATOR, 'g'), csv.FRACTION_SIGN);
    const lines = [];
    if (computeValue !== 0) {
      lines.push(['Compute discounts:', format(computeValue)]);
    }
    if (storageValue !== 0) {
      lines.push(['Storage discounts:', format(storageValue)]);
    }
    csv.addLines(...lines);
    resolve();
  });
}

export default compose;
