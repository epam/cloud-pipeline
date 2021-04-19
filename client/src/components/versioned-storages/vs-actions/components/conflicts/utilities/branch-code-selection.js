/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

function getAllTextNodes (node) {
  const result = [];
  if (node && node.hasChildNodes()) {
    for (let c = 0; c < node.childNodes.length; c++) {
      const child = node.childNodes[c];
      if (child.nodeType === Node.TEXT_NODE) {
        result.push(child);
      } else if (child.nodeType === Node.ELEMENT_NODE) {
        result.push(...getAllTextNodes(child));
      }
    }
  }
  return result;
}

function findVSLine (node) {
  if (!node) {
    return node;
  }
  if (node && node.dataset && node.dataset.hasOwnProperty('vsLine')) {
    return node;
  }
  if (!node.parentNode) {
    return undefined;
  }
  return findVSLine(node.parentNode);
}

function isLastSelectionLine (vsLineNode, range) {
  const {
    endContainer
  } = range;
  return vsLineNode === endContainer ||
    getAllTextNodes(vsLineNode).indexOf(endContainer) >= 0;
}

function getVSLineSelectionText (vsLineNode, range) {
  const {
    startOffset = 0,
    startContainer,
    endOffset = 0,
    endContainer
  } = range;
  const result = [];
  const children = getAllTextNodes(vsLineNode);
  let include = vsLineNode !== startContainer && children.indexOf(startContainer) === -1;
  for (let c = 0; c < children.length; c++) {
    const child = children[c];
    let from = 0;
    let to = Infinity;
    if (child === startContainer || vsLineNode === startContainer) {
      include = true;
      from = startOffset;
    }
    if (child === endContainer || vsLineNode === endContainer) {
      to = endOffset;
    }
    if (include) {
      result.push((child.textContent || '').slice(from, to));
    }
    if (child === endContainer || vsLineNode === endContainer) {
      break;
    }
  }
  return result.join(' ');
}

export default function getBranchCodeFromSelection (selection) {
  if (!selection) {
    return [];
  }
  const range = selection.getRangeAt(0);
  const {
    startContainer,
    endContainer,
    commonAncestorContainer
  } = range;
  const isBranch = commonAncestorContainer &&
    commonAncestorContainer.dataset &&
    commonAncestorContainer.dataset.vsBranch;
  if (!isBranch || selection.isCollapsed || startContainer === endContainer) {
    return selection.toString();
  }
  let lineContainer = findVSLine(startContainer);
  if (lineContainer) {
    const result = [];
    while (lineContainer) {
      if (
        lineContainer.dataset &&
        lineContainer.dataset.hasOwnProperty('vsLine') &&
        (
          !lineContainer.dataset.hasOwnProperty('vsSkipLine') ||
          lineContainer.dataset.vsSkipLine !== 'true'
        )
      ) {
        result.push(getVSLineSelectionText(lineContainer, range));
      }
      if (isLastSelectionLine(lineContainer, range)) {
        break;
      }
      lineContainer = lineContainer.nextSibling;
    }
    return result.join('\n');
  }
  return selection.toString();
}
