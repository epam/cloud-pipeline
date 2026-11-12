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
import classNames from 'classnames';
import escapeRegExp from '../../../utils/escape-reg-exp';

// The characters ansi_up escapes in the text of its HTML output, and their escapes
const HTML_ESCAPES = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  '\'': '&#x27;'
};

const HTML_ENTITIES = Object.keys(HTML_ESCAPES)
  .reduce((entities, character) => ({...entities, [HTML_ESCAPES[character]]: character}), {});

function escapeHtml (text) {
  return text.replace(/[&<>"']/g, character => HTML_ESCAPES[character]);
}

function unescapeHtml (html) {
  return html.replace(/&(?:amp|lt|gt|quot|#x27);/g, entity => HTML_ENTITIES[entity]);
}

export function createAnsiConverter () {
  return new AnsiUp();
}

/**
 * Converts a log line to HTML. The colors carry over to the next line, as in a terminal,
 * so convert the lines of a log in their order, with one converter.
 * ansi_up is a streaming parser: it keeps an escape sequence that is not complete
 * at the end of its input, and puts it in front of the next input.
 * An unterminated hyperlink would then hide all the lines after it,
 * so an incomplete sequence is dropped instead. `_buffer` is not a public field
 * of ansi_up: log-line-html.test.js checks it after an update of ansi_up.
 * @param {AnsiUp} converter
 * @param {string} line
 * @returns {string}
 */
export function ansiToHtml (converter, line) {
  converter._buffer = '';
  return converter.ansi_to_html(line || '');
}

/**
 * Marks the matches of the search string in a text part of a log line's HTML.
 * The part is escaped by ansi_up, so the search runs on its unescaped text:
 * a match does not start or end inside an escaped character, such as &quot;
 * @param {string} source - the HTML of a text part, with no tags
 * @param {string} search
 * @param {{lastIndex: number, currentIndex: number}} options
 * @returns {{result: string, positions: number[]}} - positions are the matches' offsets
 * in the source HTML
 */
function markSearch (source, search, options = {}) {
  const {
    lastIndex = 0,
    currentIndex
  } = options;
  const text = unescapeHtml(source);
  const searchRegExp = new RegExp(`(${escapeRegExp(search)})`, 'ig');
  let result = '';
  let e = searchRegExp.exec(text);
  let position = 0;
  let htmlPosition = 0;
  const positions = [];
  while (e) {
    const classes = classNames(
      'cp-search-highlight-text',
      {
        inactive: currentIndex !== undefined && lastIndex + positions.length !== currentIndex,
        active: currentIndex !== undefined && lastIndex + positions.length === currentIndex
      }
    );
    const before = escapeHtml(text.slice(position, e.index));
    const match = escapeHtml(e[0]);
    htmlPosition += before.length;
    positions.push(htmlPosition);
    htmlPosition += match.length;
    result = result
      .concat(before)
      .concat(`<span class="${classes}">`)
      .concat(match)
      .concat('</span>');
    position = e.index + e[0].length;
    e = searchRegExp.exec(text);
  }
  if (positions.length === 0) {
    return {
      result: source,
      positions
    };
  }
  result = result.concat(escapeHtml(text.slice(position)));
  return {
    result,
    positions
  };
}

/**
 * Marks the matches of the search string in a log line's HTML, as ansi_up renders it.
 * A match is searched in each text part between the tags.
 * @param {string} html
 * @param {string} text - the search string
 * @param {{lastIndex: number, currentIndex: number}} options - currentIndex is the index
 * of the current match, which is marked as active; the other matches are marked as inactive
 * @returns {{result: string, positions: number[], lastIndex: number}}
 */
export function findText (html, text, options = {}) {
  const {
    lastIndex = 0,
    currentIndex
  } = options;
  const tagRegExp = /<[^>]+>/g;
  let result = '';
  let e = tagRegExp.exec(html);
  let position = 0;
  const positions = [];
  let index = lastIndex;
  while (e) {
    const {
      result: marked,
      positions: partPositions = []
    } = markSearch(html.slice(position, e.index), text, {lastIndex: index, currentIndex});
    index += partPositions.length;
    positions.push(...partPositions.map((n) => n + position));
    result = result
      .concat(marked)
      .concat(e[0]);
    position = e.index + e[0].length;
    e = tagRegExp.exec(html);
  }
  const {
    result: other,
    positions: otherPositions = []
  } = markSearch(html.slice(position), text, {lastIndex: index, currentIndex});
  positions.push(...otherPositions.map((n) => n + position));
  result = result.concat(other);
  return {
    result,
    positions,
    lastIndex: index
  };
}
