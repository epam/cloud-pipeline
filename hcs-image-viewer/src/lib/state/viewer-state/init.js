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

export default function init() {
  const selections = [];
  return {
    identifiers: [],
    channels: [],
    channelsVisibility: [],
    selections,
    builtForSelections: selections,
    globalSelection: undefined,
    colors: [],
    domains: [],
    contrastLimits: [],
    useLens: false,
    useColorMap: false,
    colorMap: '',
    lensEnabled: false,
    lensChannel: 0,
    use3D: false,
    pixelValues: [],
    xSlice: [0, 1],
    ySlice: [0, 1],
    zSlice: [0, 1],
    ready: false,
    isRGB: false,
    shapeIsInterleaved: false,
    pending: false,
    globalDimensions: [],
    metadata: undefined,
    loader: [],
    error: undefined,
  };
};
