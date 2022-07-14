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

import getImageSize from './get-image-size';

/**
 * Gets minimum zoom level
 * @param {TiffPixelSource|TiffPixelSource[]} source
 * @param {{width: number, height: number}} viewSize
 * @param {number} [zoomBackOff=0]
 * @return {number}
 */
export default function getZoomLevel(source, viewSize, zoomBackOff = 0) {
  const firstSource = Array.isArray(source) ? source[0] : source;
  const size = getImageSize(firstSource);
  if (size && viewSize) {
    const { width, height } = size;
    return Math.log2(Math.min(viewSize.width / width, viewSize.height / height))
      - zoomBackOff;
  }
  return -Infinity;
}
