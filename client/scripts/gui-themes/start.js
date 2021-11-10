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

const generateThemes = require('./generate-themes');
const fs = require('fs');
const path = require('path');

generateThemes();
const themesDirectory = path.resolve(__dirname, '../../src/themes');

function updateCallback (options) {
  const {
    skip,
    test
  } = options || {};
  return function (event, filename) {
    if (/^rename$/i.test(event)) {
      return;
    }
    if (/(theme\.less\.template\.js|-theme[/\\]index\.js)$/i.test(filename)) {
      return;
    }
    if (test && !test.test(filename)) {
      return;
    }
    if (skip && skip.test(filename)) {
      return;
    }
    console.log(filename, event, '. rebuilding themes...');
    generateThemes();
    console.log('done');
  };
}

fs.watch(themesDirectory, {recursive: true}, updateCallback());
