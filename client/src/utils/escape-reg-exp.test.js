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

import escapeRegExp, {ESCAPE_CHARACTERS} from './escape-reg-exp';

describe('escapeRegExp', () => {
  it('leaves a string with no special characters alone', () => {
    expect(escapeRegExp('storage')).toBe('storage');
  });

  it('escapes every character it claims to', () => {
    ESCAPE_CHARACTERS.forEach(character => {
      expect(escapeRegExp(character)).toBe(`\\${character}`);
    });
  });

  it('escapes every occurrence, not just the first', () => {
    expect(escapeRegExp('a.b.c')).toBe('a\\.b\\.c');
  });

  it('produces a pattern that matches the original literally', () => {
    const literal = 'run-1.2 (draft) [x]';
    expect(new RegExp(escapeRegExp(literal)).test(literal)).toBe(true);
  });

  it('honours an explicit character list', () => {
    expect(escapeRegExp('a.b-c', ['.'])).toBe('a\\.b-c');
  });
});
