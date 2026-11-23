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

import {formOffersCapacityBlocks} from './capacity-blocks';

describe('formOffersCapacityBlocks', () => {
  it('offers capacity blocks on the launch form', () => {
    expect(formOffersCapacityBlocks({})).toBe(true);
    expect(formOffersCapacityBlocks({isDetachedConfiguration: false})).toBe(true);
  });

  it('does not offer them for a detached configuration', () => {
    expect(formOffersCapacityBlocks({detached: true, editConfigurationMode: true})).toBe(false);
  });

  it('does not offer them for a pipeline configuration', () => {
    expect(formOffersCapacityBlocks({editConfigurationMode: true})).toBe(false);
  });

  it('does not offer them for a launch profile', () => {
    expect(formOffersCapacityBlocks({launchProfile: true})).toBe(false);
    expect(formOffersCapacityBlocks({launchProfile: {onSave: () => {}}})).toBe(false);
  });
});
