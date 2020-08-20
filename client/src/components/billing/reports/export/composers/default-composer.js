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

import Fields from './fields';

function getExtraFields (configuration) {
  if (!configuration) {
    return [];
  }
  const wrapField = (field) => {
    if (typeof field === 'string') {
      return (o) => o[field];
    }
    return field;
  };
  if (Array.isArray(configuration)) {
    return configuration.map(o => ({
      key: o,
      field: wrapField(o)
    }));
  }
  return Object.keys(configuration).map(key => ({
    key,
    field: wrapField(configuration[key])
  }));
}

function compose (csv, discounts, request, extra) {
  return new Promise((resolve, reject) => {
    if (!request.loaded) {
      reject(new Error('Billing centers data is not available'));
    } else {
      const {value = {}} = request;
      const extraFields = getExtraFields(extra);
      const objects = Object.keys(value);
      objects.forEach(name => {
        const obj = value[name];
        csv.setCellValue(
          Fields.summaryPrevious,
          name,
          obj.previous
        );
        csv.setCellValue(
          Fields.summaryCurrent,
          name,
          obj.value
        );
        extraFields.forEach(({key, field}) => {
          csv.setCellValue(
            key,
            name,
            field(obj)
          );
        });
      });
      resolve();
    }
  });
}

export default compose;
