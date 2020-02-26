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

function getConfigurations (obj, paths = []) {
  if (!obj) {
    return [];
  }
  const keys = Object.keys(obj);
  const children = [];
  function match (test) {
    return this._r.test(test);
  }
  for (let i = 0; i < keys.length; i++) {
    const key = keys[i];
    if (
      obj.hasOwnProperty(key) &&
      typeof obj[key] === 'object' &&
      obj[key].hasOwnProperty('path')
    ) {
      obj[key].name = [...paths, key].join('.');
      obj[key]._r = new RegExp(`^${obj[key].path}/?$`, 'i');
      obj[key].match = match.bind(obj[key]);
      children.push(obj[key]);
      children.push(...(getConfigurations(obj[key], [...paths, key])));
    }
  }
  return children;
}

const reportsRouting = {
  general: {
    path: '/billing/reports'
  },
  storages: {
    path: '/billing/reports/storage',
    file: {
      path: '/billing/reports/storage/file'
    },
    object: {
      path: '/billing/reports/storage/object'
    }
  },
  instances: {
    path: '/billing/reports/instance',
    cpu: {
      path: '/billing/reports/instance/cpu'
    },
    gpu: {
      path: '/billing/reports/instance/gpu'
    }
  },
  configurations: [],
  parse: function ({pathname}) {
    const [match] = this.configurations.filter(c => c.match(pathname));
    return match?.name;
  },
  getPath: function (name) {
    const [match] = this.configurations.filter(c => c.name === name);
    return match?.path || this.general.path;
  }
};

reportsRouting.configurations = getConfigurations(reportsRouting);

export default reportsRouting;
