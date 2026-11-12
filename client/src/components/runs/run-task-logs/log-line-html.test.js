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

import {ansiToHtml, createAnsiConverter, findText} from './log-line-html';

const RED = 'color:rgb(187,0,0)';

const toHtml = (line) => ansiToHtml(createAnsiConverter(), line);

const mark = (text, state) => {
  const classes = ['cp-search-highlight-text', state].filter(Boolean).join(' ');
  return `<span class="${classes}">${text}</span>`;
};

describe('ansiToHtml', () => {
  it('renders the ANSI colors and styles', () => {
    expect(toHtml('\u001b[1;31mERROR\u001b[0m: failed'))
      .toBe(`<span style="font-weight:bold;${RED}">ERROR</span>: failed`);
  });

  it('escapes the HTML special characters, the quotes included', () => {
    expect(toHtml('<img src="x" onerror=\'alert(1)\'> & more'))
      .toBe('&lt;img src=&quot;x&quot; onerror=&#x27;alert(1)&#x27;&gt; &amp; more');
  });

  it('renders an http(s) hyperlink and drops a javascript one', () => {
    expect(toHtml('\u001b]8;;https://example.com/?a="b"\u0007site\u001b]8;;\u0007'))
      .toBe('<a href="https://example.com/?a=&quot;b&quot;">site</a>');
    expect(toHtml('\u001b]8;;javascript:alert(1)\u0007click\u001b]8;;\u0007 end'))
      .toBe(' end');
  });

  it('carries a color over to the next line', () => {
    const converter = createAnsiConverter();
    expect(ansiToHtml(converter, '\u001b[31mfirst')).toBe(`<span style="${RED}">first</span>`);
    expect(ansiToHtml(converter, 'second')).toBe(`<span style="${RED}">second</span>`);
    expect(ansiToHtml(converter, '\u001b[0mthird')).toBe('third');
  });

  it('does not carry an incomplete escape sequence over to the next line', () => {
    const converter = createAnsiConverter();
    expect(ansiToHtml(converter, 'first\u001b')).toBe('first');
    expect(ansiToHtml(converter, '[31msecond')).toBe('[31msecond');
  });

  it('does not hide the lines after an unterminated hyperlink', () => {
    const converter = createAnsiConverter();
    expect(ansiToHtml(converter, 'link \u001b]8;;http://example.com\u0007text')).toBe('link ');
    expect(ansiToHtml(converter, 'next line')).toBe('next line');
  });

  it('returns an empty string for an empty line', () => {
    expect(toHtml('')).toBe('');
    expect(toHtml(undefined)).toBe('');
  });
});

describe('findText', () => {
  it('marks every match, in any case', () => {
    const {result, positions} = findText(toHtml('Error: an error'), 'error');
    expect(result).toBe(`${mark('Error')}: an ${mark('error')}`);
    expect(positions).toEqual([0, 10]);
  });

  it('marks a match in a colored part, and keeps the tags', () => {
    expect(findText(toHtml('\u001b[31mfailed\u001b[0m: retry'), 'fail').result)
      .toBe(`<span style="${RED}">${mark('fail')}ed</span>: retry`);
  });

  it('does not search in a hyperlink address', () => {
    const html = toHtml('\u001b]8;;https://example.com/error\u0007error\u001b]8;;\u0007');
    expect(findText(html, 'error').result)
      .toBe(`<a href="https://example.com/error">${mark('error')}</a>`);
  });

  it('finds a search string with quotes', () => {
    expect(findText(toHtml('say "hello" to \'all\''), '"hello"').result)
      .toBe(`say ${mark('&quot;hello&quot;')} to &#x27;all&#x27;`);
  });

  it('finds the HTML special characters as characters', () => {
    expect(findText(toHtml('a & b <c>'), '& b <').result)
      .toBe(`a ${mark('&amp; b &lt;')}c&gt;`);
  });

  it('does not find a match inside an escaped character', () => {
    const html = toHtml('say "hi" & <b>');
    ['quot', 'amp', 'lt;', 'x27'].forEach(search => {
      expect(findText(html, search)).toEqual({result: html, positions: [], lastIndex: 0});
    });
  });

  it('gives the offsets of the matches in the HTML', () => {
    expect(findText(toHtml('"a" "a"'), 'a').positions).toEqual([6, 20]);
  });

  it('marks the current match as active and the others as inactive', () => {
    expect(findText(toHtml('ab \u001b[31mab'), 'ab', {currentIndex: 1}).result)
      .toBe(`${mark('ab', 'inactive')} <span style="${RED}">${mark('ab', 'active')}</span>`);
    expect(findText(toHtml('ab'), 'ab', {lastIndex: 2, currentIndex: 2}).result)
      .toBe(mark('ab', 'active'));
  });
});
