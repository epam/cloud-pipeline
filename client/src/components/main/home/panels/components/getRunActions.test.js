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

import {pipelineRun, stubApi} from '@test';
import preferences from '../../../../../models/preferences/PreferencesLoad';
import getRunActions from './getRunActions';

const PAUSE_ICON = 'pause-circle-o';
// The versioned storages action carries a component as its title, so it is
// told apart by its icon.
const VERSIONED_STORAGES_ICON = 'fork';

function getActions (run) {
  return getRunActions({vsActions: {available: true}, preferences}, {})(run);
}

beforeEach(async () => {
  stubApi({'/preferences': []});
  preferences.invalidateCache();
  await preferences.fetch();
});

it('offers PAUSE and versioned storages for a running run', () => {
  const icons = getActions(pipelineRun()).map((action) => action.icon);

  expect(icons).toEqual(expect.arrayContaining([PAUSE_ICON, VERSIONED_STORAGES_ICON]));
});

it('offers neither PAUSE nor versioned storages when the run node is unavailable', () => {
  const icons = getActions(pipelineRun({tags: {NODE_UNAVAILABLE: 'true'}}))
    .map((action) => action.icon);

  expect(icons).not.toContain(PAUSE_ICON);
  expect(icons).not.toContain(VERSIONED_STORAGES_ICON);
});

it('still offers SSH and STOP when the run node is unavailable', () => {
  const titles = getActions(pipelineRun({tags: {NODE_UNAVAILABLE: 'true'}}))
    .map((action) => action.title);

  expect(titles).toEqual(expect.arrayContaining(['SSH', 'STOP']));
});
