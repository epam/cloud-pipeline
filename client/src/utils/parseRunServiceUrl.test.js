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

import parseRunServiceUrl from './parseRunServiceUrl';

describe('parseRunServiceUrl', () => {
  it('returns an empty list for nothing to parse', () => {
    expect(parseRunServiceUrl(undefined)).toEqual([]);
    expect(parseRunServiceUrl('')).toEqual([]);
  });

  it('parses the JSON form the API sends today', () => {
    const url = '[{"name":"NoMachine","url":"https://host/nm","isDefault":true}]';
    expect(parseRunServiceUrl(url)).toEqual([
      {name: 'NoMachine', url: 'https://host/nm', isDefault: true}
    ]);
  });

  it('falls back to an unnamed single entry for a bare URL', () => {
    expect(parseRunServiceUrl('https://host/service')).toEqual([
      {name: null, url: 'https://host/service'}
    ]);
  });

  it('splits the legacy semicolon-separated form', () => {
    expect(parseRunServiceUrl('https://host/a;https://host/b')).toEqual([
      {name: null, url: 'https://host/a'},
      {name: null, url: 'https://host/b'}
    ]);
  });

  it('returns whatever the JSON parsed to, which need not be a list', () => {
    // A value that happens to be valid JSON never reaches the split branch, so
    // a port number comes back as a number. Callers that iterate the result
    // have to cope with this.
    expect(parseRunServiceUrl('8080')).toBe(8080);
  });
});
