/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import Panels from './panels';

const defaultSizes = {
  [Panels.summary]: {w: 5, h: 1},
  [Panels.storages]: {w: 7, h: 1},
  [Panels.storagesTable]: {w: 4, h: 1}
};

const defaultObjectsSizes = {
  [Panels.summary]: {w: 5, h: 1},
  [Panels.storageLayers]: {w: 4, h: 1},
  [Panels.storages]: {w: 7, h: 1},
  [Panels.storagesTable]: {w: 4, h: 1}
};

export {defaultSizes, defaultObjectsSizes};
