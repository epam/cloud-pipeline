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

/**
 * Returns line Y coordinates (start - end)
 * @param line
 * @param branch
 * @param lineHeight {number} line height in pixels
 * @returns {{y1: *, y2: *}}
 */
function getLinePositionInfo (line, branch, lineHeight) {
  const isChange = !!line.change[branch];
  const isFirst = isChange && line.change[branch].start === line;
  const isLast = isChange && line.change[branch].end === line;
  const y1 = (line.lineNumber[branch] - 1) * lineHeight +
    line.changesBefore[branch] * 2 +
    (isChange && !isFirst ? 1 : 0);
  const y2 = y1 +
    lineHeight +
    (isChange ? 1 : 0) +
    (isChange && isLast ? 1 : 0);
  return {y1, y2};
}

function findItemIndex (lines, branch, index, position, lineHeight) {
  if (index < 0) {
    return 0;
  }
  if (index >= (lines || []).length) {
    return (lines || []).length - 1;
  }
  const line = lines[index];
  const {y1, y2} = getLinePositionInfo(line, branch, lineHeight);
  if (y1 >= position && position <= y2) {
    return index + (position - y1) / (y2 - y1);
  } else if (position < y1) {
    return findItemIndex(lines, branch, index - 1, position, lineHeight);
  } else {
    return findItemIndex(lines, branch, index + 1, position, lineHeight);
  }
}

export function findLineIndexByPosition (position, lines, branch, lineHeight) {
  if (!lines || !lines.length) {
    return 0;
  }
  const startIndex = Math.max(
    0,
    Math.min(
      (lines || []).length,
      Math.floor(position / lineHeight)
    )
  );
  return findItemIndex(lines, branch, startIndex, position, lineHeight);
}

export function findLinePositionByIndex (lines, index, branch, lineHeight) {
  if (!lines || !lines.length) {
    return 0;
  }
  const indexCorrected = Math.max(
    0,
    Math.min(
      (lines || []).length,
      Math.floor(index)
    )
  );
  const line = lines[indexCorrected];
  const {y1, y2} = getLinePositionInfo(line, branch, lineHeight);
  const indexFractionPart = index - indexCorrected;
  return y1 + (y2 - y1) * indexFractionPart;
}
