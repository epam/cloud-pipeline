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

import isInterleaved from './is-interleaved';

// source: https://github.com/hms-dbmi/viv/blob/master/src/loaders/utils.ts
/**
 * Gets source size
 * @param {TiffPixelSource} source
 * @return {undefined|{width: number, height: number}}
 */
export default function getImageSize(source) {
  if (source && source.shape) {
    const interleaved = isInterleaved(source.shape);
    const [height, width] = source.shape.slice(interleaved ? -3 : -2);
    return { height, width };
  }
  return undefined;
}
