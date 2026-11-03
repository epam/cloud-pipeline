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

/* eslint-env jest */

import AnsiUp from 'ansi_up';
import {ansiToHtml, ansiToText, highlightSearchResults} from './log-text-utilities';

const DEFAULT = '#877616';
const ACTIVE = '#dbbf24';
const mark = (text, color = DEFAULT) => `<span style="background-color:${color}">${text}</span>`;

function highlight (line, searchString, isActive = false, position = -1) {
  const html = new AnsiUp().ansi_to_html(line);
  const text = ansiToText(line).toLowerCase();
  return highlightSearchResults(html, text, searchString, DEFAULT, ACTIVE, isActive, position);
}

describe('ansiToHtml', () => {
  it('does not carry an incomplete escape sequence over to the next line', () => {
    const converter = new AnsiUp();
    expect(ansiToHtml(converter, 'first\u001b')).toBe('first');
    expect(ansiToHtml(converter, '[31msecond')).toBe('[31msecond');
  });

  it('does not carry a color over to the next line', () => {
    const converter = new AnsiUp();
    expect(ansiToHtml(converter, '\u001b[31mred'))
      .toBe('<span style="color:rgb(187,0,0)">red</span>');
    expect(ansiToHtml(converter, 'plain')).toBe('plain');
  });
});

describe('ansiToText', () => {
  it('removes the ANSI codes', () => {
    expect(ansiToText('\u001b[1;31mERROR\u001b[0m: failed')).toBe('ERROR: failed');
  });

  it('returns the HTML special characters as text', () => {
    expect(ansiToText('<img src="x" onerror=\'alert(1)\'> & more'))
      .toBe('<img src="x" onerror=\'alert(1)\'> & more');
  });

  it('does not lose the lines after an unclosed hyperlink', () => {
    expect(ansiToText('link \u001b]8;;http://example.com\u0007text')).toBe('link ');
    expect(ansiToText('next line')).toBe('next line');
  });

  it('returns an empty string for an empty line', () => {
    expect(ansiToText('')).toBe('');
    expect(ansiToText(undefined)).toBe('');
  });
});

describe('highlightSearchResults', () => {
  it('highlights a match after an escaped character', () => {
    expect(highlight('say "hello"', 'hello'))
      .toBe(`say &quot;${mark('hello')}&quot;`);
  });

  it('highlights an escaped character as a whole', () => {
    expect(highlight('a < b', '<'))
      .toBe(`a ${mark('&lt;')} b`);
    expect(highlight('it\'s', 't\'s'))
      .toBe(`i${mark('t')}${mark('&#x27;')}${mark('s')}`);
  });

  it('highlights a match that crosses a color', () => {
    expect(highlight('\u001b[31merr\u001b[0mor', 'error'))
      .toBe(`<span style="color:rgb(187,0,0)">${mark('err')}</span>${mark('or')}`);
  });

  it('highlights the current match with the active color', () => {
    expect(highlight('ab ab', 'ab', true, 3))
      .toBe(`${mark('ab')} ${mark('ab', ACTIVE)}`);
  });

  it('keeps a line without a match as it is', () => {
    expect(highlight('nothing "here"', 'xyz'))
      .toBe('nothing &quot;here&quot;');
  });
});
