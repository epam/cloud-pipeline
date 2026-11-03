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

import AnsiUp from 'ansi_up';

// The escapes ansi_up applies to the text of its HTML output
const HTML_ENTITIES = {
  '&amp;': '&',
  '&lt;': '<',
  '&gt;': '>',
  '&quot;': '"',
  '&#x27;': '\''
};

const HTML_ENTITY_REGEXP = /&(?:amp|lt|gt|quot|#x27);/g;
const HTML_TOKEN_REGEXP = /(<[^>]*>|&(?:amp|lt|gt|quot|#x27);)/g;

const textConverter = new AnsiUp();

/**
 * Converts a log line to HTML, starting from a clean converter state.
 * ansi_up is a streaming parser: it keeps an incomplete escape sequence
 * at the end of its input, and puts it in front of the next input.
 * The log lines are converted one by one and in any order,
 * so nothing must be carried over from one line to another.
 * @param {AnsiUp} converter
 * @param {string} line
 * @returns {string}
 */
export function ansiToHtml (converter, line) {
  converter._buffer = '';
  converter.bg = null;
  converter.fg = null;
  return converter.ansi_to_html(line || '');
}

/**
 * Returns a log line's text, as ansi_up renders it, without the ANSI codes.
 * ansi_up 5 has no ansi_to_text, so the text is taken from its HTML output.
 * @param {string} line
 * @returns {string}
 */
export function ansiToText (line) {
  return ansiToHtml(textConverter, line)
    .replace(/<[^>]*>/g, '')
    .replace(HTML_ENTITY_REGEXP, entity => HTML_ENTITIES[entity]);
}

/**
 * Highlights the matches of the search string in a log line's HTML.
 * The positions are found in the line's text, and an escaped character
 * of the HTML counts as the one character it stands for.
 * @param {string} html - the line, as ansi_up renders it
 * @param {string} text - the line's text, as ansiToText returns it, lower cased
 * @param {string} searchString - a non-empty, lower cased search string
 * @param {string} colorDefault - the background of a match
 * @param {string} colorActive - the background of the current match
 * @param {boolean} isActive - whether the current match is in this line
 * @param {number} position - the position of the current match in the text
 * @returns {string}
 */
export function highlightSearchResults (
  html,
  text,
  searchString,
  colorDefault,
  colorActive,
  isActive,
  position
) {
  const parts = html.split(HTML_TOKEN_REGEXP);
  const matchRegExp = new RegExp(searchString.replace(/[-[\]{}()*+?.,\\^$|#\s]/g, '\\$&'), 'g');
  let result;
  const ranges = [];
  while ((result = matchRegExp.exec(text)) !== null) {
    ranges.push({
      start: result.index,
      end: result.index + searchString.length
    });
  }
  const resultParts = [];
  let index = 0;
  for (let i = 0; i < parts.length; i++) {
    const part = parts[i];
    if (part.startsWith('<') && part.endsWith('>')) {
      resultParts.push(part);
      continue;
    }
    const isEntity = Object.prototype.hasOwnProperty.call(HTML_ENTITIES, part);
    const length = isEntity ? 1 : part.length;
    const start = index;
    const end = index + length;
    let modifiedPart = part;
    for (let r = ranges.length - 1; r >= 0; r--) {
      const range = ranges[r];
      if (range.start < end && range.end > start) {
        const color = isActive && range.start === position ? colorActive : colorDefault;
        const selectFrom = isEntity ? 0 : Math.max(range.start, start) - index;
        const selectTo = isEntity ? part.length : Math.min(range.end, end) - index;
        const before = modifiedPart.substring(0, selectFrom);
        const match = modifiedPart.substring(selectFrom, selectTo);
        const after = modifiedPart.substring(selectTo);
        modifiedPart = `${before}<span style="background-color:${color}">${match}</span>${after}`;
      }
    }
    resultParts.push(modifiedPart);
    index += length;
  }
  return resultParts.join('');
}
