/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import { Viewer } from './lib';
import { generateUrls } from './.test/index';

const viewer = new Viewer({
  container: document.getElementById('root'),
  verbose: true,
  style: {
    width: '100%',
    height: '100%',
    border: '1px solid black',
  },
});

window.addEventListener('message', (message) => {
  const { data } = message;
  const { type, method, options = [] } = data;
  if (type === 'hcs' && viewer[method] && typeof viewer[method] === 'function') {
    viewer[method](...options);
  }
});

generateUrls()
  .then((urls) => {
    const {
      url,
      offsets,
    } = urls;
    let o = 0;
    const presets = [
      { url, offsets },
    ];

    const next = () => {
      const preset = presets[o % presets.length];
      console.log(`#${o}: ${preset.url}`);
      o += 1;
      viewer
        .setData(preset.url, preset.offsets)
        .catch((e) => console.warn(e.message))
        .then(() => {
          // setTimeout(next, 1000);
        });
    };

    next();
  });
