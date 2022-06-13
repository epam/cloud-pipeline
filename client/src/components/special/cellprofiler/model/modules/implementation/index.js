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

import {IdentifyPrimaryObjects} from './identify-primary-objects';
import {IdentifySecondaryObjects} from './identify-secondary-objects';
import {RelateObjects} from './relate-objects';
import {OverlayOutlines} from './overlay-outlines';
import {NamesAndTypes} from './names-and-types';
import {SaveImages} from './save-images';

const allModules = [
  IdentifyPrimaryObjects,
  IdentifySecondaryObjects,
  RelateObjects,
  OverlayOutlines,
  SaveImages,
  NamesAndTypes
];

export {
  allModules,
  IdentifyPrimaryObjects,
  IdentifySecondaryObjects,
  OverlayOutlines,
  RelateObjects,
  SaveImages,
  NamesAndTypes
};
