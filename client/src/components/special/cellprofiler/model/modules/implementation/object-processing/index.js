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

import ConvertObjectsToImage from './convert-objects-to-image';
import ExpandOrShrinkObjects from './expand-or-shrink-objects';
import FilterObjects, {
  FilterObjectsBySize
} from './filter-objects';
import IdentifyPrimaryObjects from './identify-primary-objects';
import IdentifySecondaryObjects from './identify-secondary-objects';
import IdentifyTertiaryObjects from './identify-tertiary-objects';
import MaskObjects from './mask-objects';
import RelateObjects from './relate-objects';
import ResizeObjects from './resize-objects';
import SplitOrMergeObjects from './split-or-merge-objects';

export default [
  ConvertObjectsToImage,
  ExpandOrShrinkObjects,
  FilterObjects,
  FilterObjectsBySize,
  IdentifyPrimaryObjects,
  IdentifySecondaryObjects,
  IdentifyTertiaryObjects,
  MaskObjects,
  RelateObjects,
  ResizeObjects,
  SplitOrMergeObjects
];
