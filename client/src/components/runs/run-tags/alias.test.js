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

import {applyRunNameAliasTag} from './alias';

describe('applyRunNameAliasTag', () => {
  it('adds the alias and keeps every tag already set', () => {
    const payload = {tags: {owner: 'me', project: 'required-tag-value'}, runNameAlias: 'my-run'};
    applyRunNameAliasTag(payload, payload.runNameAlias);
    expect(payload.tags).toEqual({owner: 'me', project: 'required-tag-value', alias: 'my-run'});
  });

  it('removes the runNameAlias field', () => {
    const payload = {tags: {}, runNameAlias: 'my-run'};
    applyRunNameAliasTag(payload, payload.runNameAlias);
    expect('runNameAlias' in payload).toBe(false);
  });

  it('adds the alias to a payload with no tags', () => {
    const payload = {runNameAlias: 'my-run'};
    applyRunNameAliasTag(payload, payload.runNameAlias);
    expect(payload.tags).toEqual({alias: 'my-run'});
  });

  it('launches with the alias passed in, not the one the payload carried', () => {
    const payload = {tags: {owner: 'me'}, runNameAlias: 'old-name'};
    applyRunNameAliasTag(payload, 'new-name');
    expect(payload).toEqual({tags: {owner: 'me', alias: 'new-name'}});
  });

  it('the alias wins over a tag named "alias"', () => {
    const payload = {tags: {alias: 'old'}};
    applyRunNameAliasTag(payload, 'new');
    expect(payload.tags).toEqual({alias: 'new'});
  });

  it('does not mutate the tags object passed in', () => {
    const tags = {owner: 'me'};
    applyRunNameAliasTag({tags, runNameAlias: 'my-run'}, 'my-run');
    expect(tags).toEqual({owner: 'me'});
  });

  it('removes the alias tag when the user cleared the run name', () => {
    const payload = {tags: {owner: 'me', alias: 'my-run'}, runNameAlias: 'my-run'};
    applyRunNameAliasTag(payload, undefined);
    expect(payload).toEqual({tags: {owner: 'me'}});
  });

  it('keeps an alias tag when no run name was ever set', () => {
    const payload = {tags: {owner: 'me', alias: 'from-a-tag'}};
    applyRunNameAliasTag(payload, undefined);
    expect(payload).toEqual({tags: {owner: 'me', alias: 'from-a-tag'}});
  });

  it('leaves a payload with no alias as it was', () => {
    const tags = {owner: 'me'};
    const payload = {tags};
    expect(applyRunNameAliasTag(payload, undefined)).toBe(payload);
    expect(payload).toEqual({tags: {owner: 'me'}});
    expect(payload.tags).toBe(tags);
  });

  it('does not add tags to a payload that had none', () => {
    const payload = {};
    applyRunNameAliasTag(payload, undefined);
    expect('tags' in payload).toBe(false);
  });

  it('does not throw on a missing payload', () => {
    expect(applyRunNameAliasTag(undefined, 'my-run')).toBeUndefined();
  });
});
