/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

export function getWellMesh (well) {
  if (!well) {
    return undefined;
  }
  const {images = []} = well;
  if (images.length === 0) {
    return undefined;
  }
  const minX = Math.min(...images.map(o => o.x));
  const maxX = Math.max(...images.map(o => o.x));
  const minY = Math.min(...images.map(o => o.y));
  const maxY = Math.max(...images.map(o => o.y));
  const longestSeries = Math.max(maxX - minX + 1, maxY - minY + 1);
  return {
    columns: longestSeries,
    rows: longestSeries,
    cells: images.map(image => ({column: image.x - minX, row: maxY - image.y}))
  };
}

export function getWellImageFromMesh (well, cell) {
  if (!well || !cell) {
    return undefined;
  }
  const {images = []} = well;
  if (images.length === 0) {
    return undefined;
  }
  const minX = Math.min(...images.map(o => o.x));
  const maxY = Math.max(...images.map(o => o.y));
  return images.find(image => image.x === minX + cell.column && image.y === maxY - cell.row);
}
