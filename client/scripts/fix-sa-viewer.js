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

const fs = require('fs');
const path = require('path');

function fixFile (file) {
  try {
    let cssContent = fs.readFileSync(file).toString();
    cssContent = cssContent.replace(/; !important;/g, ';');
    [
      'position',
      'z-index',
      'absolute',
      '2',
      'bottom',
      '0px',
      '3px',
      'left',
      'height',
      'width',
      '50%',
      'background-color',
      '#404060'
    ]
      .forEach(error => {
        cssContent = cssContent.replace(new RegExp(`"${error}"`, 'g'), error);
      });
    const data = Buffer.from(cssContent);
    fs.writeFileSync(file, data);
  } catch (e) {
    console.log('Error fixing sa-viewer css file',file, ':', e.message);
  }
}

fixFile(path.join(__dirname, '../node_modules/slideatlas-viewer/css/main.css'));
fixFile(path.join(__dirname, '../node_modules/slideatlas-viewer/css/viewer.css'));
fixFile(path.join(__dirname, '../node_modules/slideatlas-viewer/css/saViewer.css'));
