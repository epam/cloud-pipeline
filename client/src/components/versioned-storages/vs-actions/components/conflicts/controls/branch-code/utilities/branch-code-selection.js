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

function getAllNodes (node) {
  const result = [];
  if (node && node.hasChildNodes()) {
    for (let c = 0; c < node.childNodes.length; c++) {
      const child = node.childNodes[c];
      result.push(child);
      if (child.hasChildNodes()) {
        result.push(...getAllNodes(child));
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
    getAllNodes(vsLineNode).indexOf(endContainer) >= 0;
}

function getVSLineSelectionText (vsLineNode, range) {
  const {
    startOffset = 0,
    startContainer,
    endOffset = 0,
    endContainer
  } = range;
  const result = [];
  const allChildren = [vsLineNode, ...getAllNodes(vsLineNode)];
  let include = allChildren.indexOf(startContainer) === -1;
  let parentContainer;
  let from = 0;
  let to = Infinity;
  const isChild = (child, parent) => {
    if (child && parent) {
      if (child === parent || child.parentNode === parent) {
        return true;
      }
      return isChild(child.parentNode, parent);
    }
    return false;
  };
  for (let c = 0; c < allChildren.length; c++) {
    const child = allChildren[c];
    if (child === startContainer) {
      include = true;
      from = child.nodeType === Node.TEXT_NODE ? startOffset : 0;
      parentContainer = child;
    }
    if (child === endContainer) {
      to = endOffset;
    }
    if (include && child.nodeType === Node.TEXT_NODE) {
      result.push((child.textContent || '').slice(from, to));
      from = 0;
      to = Infinity;
    } else if (include && !isChild(child, parentContainer)) {
      from = 0;
      to = Infinity;
      parentContainer = undefined;
    }
    if (child === endContainer) {
      break;
    }
  }
  return result.join(' ');
}

function getVSLineSelectionRangeInfo (vsLineNode, range) {
  const {
    startOffset = 0,
    startContainer,
    endOffset = 0,
    endContainer
  } = range;
  const result = {};
  const children = [vsLineNode, ...getAllNodes(vsLineNode)];
  for (let c = 0; c < children.length; c++) {
    const child = children[c];
    if (child === startContainer) {
      result.start = {
        lineKey: +(vsLineNode.dataset.vsLine),
        offset: startOffset
      };
    }
    if (child === endContainer) {
      result.end = {
        lineKey: +(vsLineNode.dataset.vsLine),
        offset: endOffset
      };
    }
  }
  return result;
}

export function getBranchCodeRangeFromSelection (selection) {
  if (!selection || selection.rangeCount === 0) {
    return undefined;
  }
  const range = selection.getRangeAt(0);
  const {
    startContainer,
    startOffset,
    endContainer,
    endOffset,
    commonAncestorContainer
  } = range;
  const vsBranch = commonAncestorContainer && commonAncestorContainer.dataset
    ? commonAncestorContainer.dataset.vsBranch
    : undefined;
  if (!vsBranch || selection.isCollapsed || startContainer === endContainer) {
    const lineContainer = findVSLine(startContainer);
    if (lineContainer) {
      return {
        start: {
          lineKey: +(lineContainer.dataset.vsLine),
          offset: startOffset
        },
        end: {
          lineKey: +(lineContainer.dataset.vsLine),
          offset: endOffset
        }
      };
    }
    return undefined;
  }
  let lineContainer = findVSLine(startContainer);
  if (lineContainer) {
    let result = {};
    while (lineContainer) {
      if (
        lineContainer.dataset &&
        lineContainer.dataset.hasOwnProperty('vsLine')
      ) {
        result = Object.assign({}, result, getVSLineSelectionRangeInfo(lineContainer, range));
      }
      if (isLastSelectionLine(lineContainer, range)) {
        break;
      }
      lineContainer = lineContainer.nextSibling;
    }
    return result;
  }
  return undefined;
}

export function getBranchCodeFromSelection (selection) {
  if (!selection) {
    return '';
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

function compareSelectionsInfoPart (a, b) {
  if (!a && !b) {
    return true;
  }
  if (!a || !b) {
    return false;
  }
  const {
    lineKey: aLineKey,
    offset: aOffset
  } = a;
  const {
    lineKey: bLineKey,
    offset: bOffset
  } = b;
  return aLineKey !== bLineKey && aOffset !== bOffset;
}

export function compareSelectionsInfo (a, b) {
  if (!a && !b) {
    return true;
  }
  if (!a || !b) {
    return false;
  }
  return compareSelectionsInfoPart(a.start, b.start) &&
    compareSelectionsInfoPart(a.end, b.end);
}
