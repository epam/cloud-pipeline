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
  for (let i = 0; i < keys.length; i++) {
    const key = keys[i];
    if (
      obj.hasOwnProperty(key) &&
      typeof obj[key] === 'object' &&
      obj[key].hasOwnProperty('path')
    ) {
      children.push({
        name: [...paths, key].join('.'),
        path: obj[key].path,
        _r: new RegExp(`^${obj[key].path}/?$`, 'i'),
        match: function (test) {
          return this._r.test(test);
        }
      });
      children.push(...(getConfigurations(obj[key], [...paths, key])));
    }
  }
  return children;
}

const routing = {
  general: {
    path: '/billing/reports'
  },
  storages: {
    path: '/billing/reports/storage',
    title: 'Storage',
    file: {
      path: '/billing/reports/storage/file',
      label: 'File'
    },
    object: {
      path: '/billing/reports/storage/object',
      label: 'Object'
    }
  },
  instances: {
    path: '/billing/reports/instance',
    title: 'Compute instances',
    cpu: {
      path: '/billing/reports/instance/cpu',
      label: 'CPU'
    },
    gpu: {
      path: '/billing/reports/instance/gpu',
      label: 'GPU'
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
  },
  getPathByChartInfo: function (label, title) {
    for (let type in routing) {
      if (routing[type].title === title && routing.hasOwnProperty(type)) {
        for (let subType in routing[type]) {
          if (routing[type][subType].label === label && routing[type].hasOwnProperty(subType)) {
            return this.getPath(`${type}.${subType}`);
          }
        }
      }
    }
    return null;
  }
};

routing.configurations = getConfigurations(routing);

export default routing;
