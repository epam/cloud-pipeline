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

// source: https://github.com/hms-dbmi/viv/blob/master/avivator/src/utils.js
export default function guessRgb({ Pixels }) {
  const {
    Channels = [],
    SizeC = 0,
    Interleaved = false,
    Type,
  } = Pixels || {};
  const numChannels = Channels.length;
  const { SamplesPerPixel = 0 } = Channels[0] || {};

  const is3Channel8Bit = numChannels === 3 && Type === 'uint8';
  const interleavedRgb = SizeC === 3 && numChannels === 1 && Interleaved;

  return SamplesPerPixel === 3 || is3Channel8Bit || interleavedRgb;
}
