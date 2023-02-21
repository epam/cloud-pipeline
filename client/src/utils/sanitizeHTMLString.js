/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

function stringToHTML (string) {
  const parser = new DOMParser();
  const doc = parser.parseFromString(string, 'text/html');
  return doc;
}

function isDangerousAttribute (name, value = '') {
  const normalizedValue = value.toLowerCase();
  if (['src', 'href', 'xlink:href'].includes(name)) {
    return normalizedValue.includes('javascript:') ||
    normalizedValue.includes('data:text/html');
  }

  if (name.startsWith('on')) {
    return true;
  }
  return false;
}

function removeTags (html, tags = []) {
  tags.forEach(tag => {
    const matchedTags = html.querySelectorAll(tag);
    for (const tag of matchedTags) {
      tag.remove();
    }
  });
  return html;
}

function removeAttributes (html, attributesToRemove) {
  const nodes = html.children;
  for (const node of nodes) {
    const attributes = node.attributes;
    for (const {name, value} of attributes) {
      if (
        isDangerousAttribute(name, value) ||
        attributesToRemove.includes(name)
      ) {
        node.removeAttribute(name);
      }
    }
    removeAttributes(node, attributesToRemove);
  }
}

/**
 * Remove tags (script by default)
 * and attributes (every on-handler and attributes that can be executable,
 * "SRC=javascript:", for example) from html string.
 * You can include any additional tags and attributes to {options}.
 * @param {string} string - html string.
 * @param {object} options - options object.
 * @returns {string} - return sanitized html string.
 */
function sanitizeHTMLString (string, options = {}) {
  try {
    const {
      tagsToRemove = ['script'],
      attributesToRemove = []
    } = options;
    const html = stringToHTML(string);
    removeTags(html, tagsToRemove);
    removeAttributes(html, attributesToRemove);
    return html.documentElement.outerHTML;
  } catch (e) {
    console.error(e);
    return '';
  }
}

export default sanitizeHTMLString;
