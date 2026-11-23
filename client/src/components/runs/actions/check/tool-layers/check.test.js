/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import {stubApi} from '@test';
import preferences from '../../../../../models/preferences/PreferencesLoad';
import checkToolLayers from './check';

const MAX_LAYERS = 50;

// the latest version has few layers, the "1.2" one has too many
async function stubTool (tool) {
  stubApi({
    '/preferences': [{name: 'commit.max.layers', value: `${MAX_LAYERS}`}],
    '/tool/load?image=library/tool&registry=registry': tool,
    '/tool/1/info': {
      versions: [
        {version: 'latest', scanResult: {layersCount: 10}},
        {version: '1.2', scanResult: {layersCount: 100}}
      ]
    }
  });
  preferences.invalidateCache();
  await preferences.fetch();
}

describe('checkToolLayers', () => {
  it('checks the default version of a tool for an image without a tag', async () => {
    await stubTool({id: 1, image: 'library/tool', defaultVersion: '1.2'});

    expect(await checkToolLayers('registry/library/tool'))
      .toEqual({result: false, allowed: MAX_LAYERS, current: 100});
  });

  it('checks the latest version of a tool without a default version', async () => {
    await stubTool({id: 1, image: 'library/tool'});

    expect(await checkToolLayers('registry/library/tool'))
      .toEqual({result: true, allowed: MAX_LAYERS, current: 10});
  });

  it('checks the version of an image with a tag', async () => {
    await stubTool({id: 1, image: 'library/tool', defaultVersion: '1.2'});

    expect(await checkToolLayers('registry/library/tool:latest'))
      .toEqual({result: true, allowed: MAX_LAYERS, current: 10});
  });
});
