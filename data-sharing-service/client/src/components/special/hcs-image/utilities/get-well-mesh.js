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

export function getWellMesh (well, fields = []) {
  if (!well) {
    return undefined;
  }
  const {
    images = [],
    meshSize
  } = well;
  if (images.length === 0 || !meshSize) {
    return undefined;
  }
  return {
    meshSize,
    cells: images.map(image => ({x: image.realX, y: image.realY, id: image.id})),
    selected: fields.map(image => ({x: image.realX, y: image.realY, id: image.id}))
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
  return images.find(image => image.id === cell.id);
}
