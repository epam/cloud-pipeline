/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

const generateThemeTemplate = require('./generate-theme-template');
const generateThemeConfiguration = require('./generate-theme-configuration');

module.exports = function () {
  generateThemeTemplate();
  generateThemeConfiguration(
    '../../src/themes/styles/variables.less',
    'light-theme',
    '../../src/themes',
    'Light'
  );
  generateThemeConfiguration(
    '../../src/themes/_dev_/dark-theme-variables.less', 'dark-theme',
    '../../src/themes',
    'Dark',
    'light-theme'
  );
  generateThemeConfiguration(
    '../../src/themes/_dev_/dark-dimmed-theme-variables.less',
    'dark-dimmed-theme',
    '../../src/themes',
    'Dark dimmed',
    'dark-theme'
  );
};
