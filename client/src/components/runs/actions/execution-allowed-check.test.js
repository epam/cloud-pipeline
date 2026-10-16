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
import preferences from '../../../models/preferences/PreferencesLoad';
import {performAsyncCheck} from './execution-allowed-check';

const EXECUTE_MASK = 4;

const dockerRegistries = (tool, tags) => ({
  loaded: true,
  fetchIfNeededOrWait: () => Promise.resolve(),
  value: {
    registries: [{
      path: 'registry',
      groups: [{name: 'library', tools: [{...tool, mask: EXECUTE_MASK}]}]
    }]
  },
  toolTags: {
    getToolTags: () => ({
      fetchIfNeededOrWait: () => Promise.resolve(),
      value: tags
    })
  }
});

const checkDockerImage = async (dockerImage, tool, tags) => {
  const {errors} = await performAsyncCheck({
    dockerImage,
    dockerRegistries: dockerRegistries(tool, tags)
  });
  return errors;
};

beforeEach(async () => {
  stubApi({'/preferences': []});
  preferences.invalidateCache();
  await preferences.fetch();
});

describe('performAsyncCheck of a docker image', () => {
  it('checks the default version of a tool for an image without a tag', async () => {
    const tool = {id: 1, image: 'library/tool', defaultVersion: '1.2'};

    expect(await checkDockerImage('registry/library/tool', tool, ['1.2'])).toHaveLength(0);
    expect(await checkDockerImage('registry/library/tool', tool, ['latest'])).toHaveLength(1);
  });

  it('checks the latest version of a tool without a default version', async () => {
    const tool = {id: 1, image: 'library/tool'};

    expect(await checkDockerImage('registry/library/tool', tool, ['latest'])).toHaveLength(0);
    expect(await checkDockerImage('registry/library/tool', tool, ['1.2'])).toHaveLength(1);
  });

  it('checks the version of an image with a tag', async () => {
    const tool = {id: 1, image: 'library/tool', defaultVersion: '1.2'};

    expect(await checkDockerImage('registry/library/tool:latest', tool, ['latest']))
      .toHaveLength(0);
  });
});
