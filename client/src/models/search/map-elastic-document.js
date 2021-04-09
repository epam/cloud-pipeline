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

import SearchItemTypes from './search-item-types';

export default function mapElasticDocument (document) {
  document.elasticId = [
    document.id || '',
    document.parentId || '',
    document.elasticId
  ].map(o => String(o)).join('-');
  switch (document.type) {
    case SearchItemTypes.azFile:
    case SearchItemTypes.s3File:
    case SearchItemTypes.NFSFile:
    case SearchItemTypes.gsFile:
      document.name = document.name.split('/').pop();
      break;
    case SearchItemTypes.dockerRegistry:
      document.name = document.name || document.description || document.path;
      break;
  }
  if (!document.description && document.text) {
    document.description = document.text;
  }
  return document;
}
