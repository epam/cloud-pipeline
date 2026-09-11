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

import {base64toString, stringToBase64} from './base64';

describe('stringToBase64', () => {
  it('encodes ASCII', () => {
    expect(stringToBase64('hello')).toBe('aGVsbG8=');
  });

  it('encodes an empty string as an empty string', () => {
    expect(stringToBase64('')).toBe('');
  });

  it('pads according to the input length', () => {
    expect(stringToBase64('a')).toBe('YQ==');
    expect(stringToBase64('ab')).toBe('YWI=');
    expect(stringToBase64('abc')).toBe('YWJj');
  });
});

describe('base64toString', () => {
  it('decodes ASCII', () => {
    expect(base64toString('aGVsbG8=')).toBe('hello');
  });

  it('decodes an empty string as an empty string', () => {
    expect(base64toString('')).toBe('');
  });
});

describe('base64 round trip', () => {
  it('survives multi-byte characters, which plain btoa cannot carry', () => {
    // The pair goes through TextEncoder/TextDecoder rather than btoa/atob
    // directly, which is the whole reason this module exists.
    const original = 'héllo — 世界';
    expect(base64toString(stringToBase64(original))).toBe(original);
  });

  it('survives a newline-bearing payload', () => {
    const original = 'line one\nline two\r\n';
    expect(base64toString(stringToBase64(original))).toBe(original);
  });
});
