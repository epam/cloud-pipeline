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

import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';
import { defaultVersionTag } from '../toolVersions';

describe('defaultVersionTag', () => {
  it('prefers the default version of a tool', () => {
    assert.equal(defaultVersionTag(['latest', '1.2', '1.1'], '1.2'), '1.2');
  });

  it('falls back to the latest version, if the default version does not exist', () => {
    assert.equal(defaultVersionTag(['latest', '1.1'], '1.2'), 'latest');
  });

  it('uses the latest version of a tool without a default version', () => {
    assert.equal(defaultVersionTag(['1.1', 'latest']), 'latest');
  });

  it('uses the only version, if there is no latest one', () => {
    assert.equal(defaultVersionTag(['1.1']), '1.1');
    assert.equal(defaultVersionTag(['1.1'], '1.2'), '1.1');
  });

  it('has no version to use among several ones without the latest one', () => {
    assert.equal(defaultVersionTag(['1.1', '1.0']), undefined);
    assert.equal(defaultVersionTag([]), undefined);
  });
});
