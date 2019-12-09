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

const colors = {
  quota: 'rgb(255, 191, 51)',
  current: 'rgb(83, 157, 210)',
  previous: 'rgb(106, 173, 79)',
  orange: 'rgb(245, 124, 62)',
  darkOrange: 'orange',
  red: 'red',
  yellow: 'rgb(255, 191, 51)',
  green: 'rgb(106, 173, 79)',
  blue: 'blue',
  darkBlue: 'darkBlue',
  gray: 'rgb(170, 170, 170)'
};

function getColorConfiguration (config, name) {
  if (!config) {
    return {};
  }
  if (name && config.hasOwnProperty(name)) {
    return config[name];
  }
  return {};
}

function getColor (config, name) {
  return getColorConfiguration(config, name).color;
}

function getBackgroundColor (config, name) {
  return getColorConfiguration(config, name).background;
}

export {colors, getColor, getBackgroundColor};
