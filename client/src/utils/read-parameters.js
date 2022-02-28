/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

function parametersToCSVString (parameters = [], options = {}) {
  const {excludedKeys} = options;
  let keys = Object.keys(Object.assign({}, ...parameters));
  if (excludedKeys && excludedKeys.length) {
    keys = keys.filter(key => !excludedKeys.includes(key));
  }
  const values = parameters.map(parameter => keys
    .map(key => {
      let value = parameter[key] || '';
      if (value.includes(',')) {
        value = `"${value}"`;
      }
      return value;
    }).join(',')
  );
  return [keys.join(','), ...values].join('\n');
}

function parametersToJSONString (parameters = [], options = {}) {
  const {excludedKeys} = options;
  const filteredParameters = parameters.map(parameter => Object.keys(parameter)
    .reduce((acc, key) => {
      if (!excludedKeys.includes(key)) {
        acc[key] = parameter[key];
      }
      return acc;
    }, {})
  );
  return JSON.stringify(filteredParameters);
}

function parametersFromJSONString (file) {
  return new Promise((resolve, reject) => {
    try {
      const reader = new FileReader();
      reader.readAsText(file);
      reader.onload = () => {
        const parsed = JSON.parse(reader.result);
        if (Array.isArray(parsed)) {
          resolve(parsed.reduce((acc, current) => {
            acc[current.name] = current;
            return acc;
          }, {}));
        } else {
          resolve({error: new Error('Error reading file content')});
        }
      };
      reader.onerror = e => resolve({
        error: new Error('Error reading file content')
      });
    } catch (e) {
      resolve({error: new Error('Error reading file content')});
    }
  });
};

function parametersFromCSVString (file) {
  return new Promise((resolve, reject) => {
    try {
      const reader = new FileReader();
      reader.readAsText(file);
      reader.onload = () => {
        const data = (reader.result || '')
          .replaceAll('\r', '')
          .split('\n')
          .filter(Boolean)
          .map(row => {
            const values = row.split(/(?!\B"[^"]*),(?![^"]*"\B)/);
            return values.map(value => {
              const isCommaEscaped = value.startsWith('"') &&
                value.endsWith('"') &&
                value.includes(',');
              if (isCommaEscaped) {
                return value.slice(1, -1);
              }
              return value;
            });
          });
        const [parameterKeys, ...rest] = data;
        const parameters = rest.reduce((acc, dataRow) => {
          const dataObject = dataRow.reduce((acc, value, index) => {
            const key = parameterKeys[index];
            acc[key] = value;
            return acc;
          }, {});
          acc[dataObject.name] = dataObject;
          return acc;
        }, {});
        resolve(parameters);
      };
      reader.onerror = e => {
        resolve({error: new Error('Error reading file content')});
      };
    } catch (e) {
      resolve({error: new Error('Error reading file content')});
    }
  });
};

export {
  parametersToCSVString,
  parametersFromCSVString,
  parametersToJSONString,
  parametersFromJSONString
};
