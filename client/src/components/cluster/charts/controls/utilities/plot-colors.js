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

const blue = '#0e77ca';
const red = '#aa2222';
const green = '#00a854';
const yellow = '#fd9135';
const aqua = '#32cecd';
const violet = '#814cfb';

export default function getThemedPlotColors (Component) {
  if (
    Component &&
    Component.props &&
    Component.props.themes &&
    Component.props.themes.currentThemeConfiguration
  ) {
    const {themes} = Component.props;
    return [
      themes.currentThemeConfiguration['@primary-color'] || blue,
      themes.currentThemeConfiguration['@color-red'] || red,
      themes.currentThemeConfiguration['@color-green'] || green,
      themes.currentThemeConfiguration['@color-yellow'] || yellow,
      themes.currentThemeConfiguration['@color-aqua'] || aqua,
      themes.currentThemeConfiguration['@color-violet'] || violet
    ];
  }
  return [
    blue,
    red,
    green,
    yellow,
    aqua,
    violet
  ];
}
