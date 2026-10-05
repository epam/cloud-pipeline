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

import {applyRunNameAliasTagToPayloads, mergeRunNameAliasTag} from './utilities';

describe('mergeRunNameAliasTag', () => {
  it('adds the alias and keeps every tag already set', () => {
    expect(mergeRunNameAliasTag({owner: 'me', team: 'core'}, 'my-run')).toEqual({
      owner: 'me',
      team: 'core',
      alias: 'my-run'
    });
  });

  it('the alias wins over a tag literally named "alias"', () => {
    expect(mergeRunNameAliasTag({alias: 'old'}, 'new')).toEqual({alias: 'new'});
  });

  it('an empty alias returns the tags unchanged', () => {
    expect(mergeRunNameAliasTag({owner: 'me'}, '')).toEqual({owner: 'me'});
  });

  it('an undefined alias returns the tags unchanged', () => {
    expect(mergeRunNameAliasTag({owner: 'me'}, undefined)).toEqual({owner: 'me'});
  });

  it('a missing alias argument returns the tags unchanged', () => {
    expect(mergeRunNameAliasTag({owner: 'me'})).toEqual({owner: 'me'});
  });

  it('undefined tags with no alias become {}', () => {
    expect(mergeRunNameAliasTag(undefined, undefined)).toEqual({});
  });

  it('does not mutate the tags object passed in', () => {
    const tags = {owner: 'me'};
    mergeRunNameAliasTag(tags, 'my-run');
    expect(tags).toEqual({owner: 'me'});
  });
});

describe('applyRunNameAliasTagToPayloads', () => {
  it('merges the alias into every payload of a multi-payload launch, not only the first', () => {
    const payloads = [
      {tags: {owner: 'me'}, runNameAlias: 'first-run'},
      {tags: {team: 'core'}, runNameAlias: 'second-run'}
    ];
    applyRunNameAliasTagToPayloads(payloads);
    expect(payloads[0].tags).toEqual({owner: 'me', alias: 'first-run'});
    expect(payloads[1].tags).toEqual({team: 'core', alias: 'second-run'});
  });

  it('removes runNameAlias from every payload, including those after the first', () => {
    const payloads = [
      {tags: {}, runNameAlias: 'first-run'},
      {tags: {}, runNameAlias: 'second-run'}
    ];
    applyRunNameAliasTagToPayloads(payloads);
    expect('runNameAlias' in payloads[0]).toBe(false);
    expect('runNameAlias' in payloads[1]).toBe(false);
  });

  it('keeps the custom and required tags already on a payload', () => {
    const payloads = [
      {tags: {owner: 'me', project: 'required-tag-value'}, runNameAlias: 'my-run'}
    ];
    applyRunNameAliasTagToPayloads(payloads);
    expect(payloads[0].tags).toEqual({
      owner: 'me',
      project: 'required-tag-value',
      alias: 'my-run'
    });
  });

  it('leaves a payload with no alias exactly as it was', () => {
    const payload = {tags: {owner: 'me'}};
    const payloads = [payload];
    applyRunNameAliasTagToPayloads(payloads);
    expect(payloads[0]).toBe(payload);
    expect(payloads[0].tags).toEqual({owner: 'me'});
    expect('alias' in payloads[0].tags).toBe(false);
  });

  it('does not throw on an empty array', () => {
    expect(() => applyRunNameAliasTagToPayloads([])).not.toThrow();
  });

  it('does not throw when called with no argument', () => {
    expect(() => applyRunNameAliasTagToPayloads()).not.toThrow();
  });
});
