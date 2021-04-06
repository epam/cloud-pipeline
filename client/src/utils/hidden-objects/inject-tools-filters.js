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

import {inject, observer} from 'mobx-react';
import {HIDDEN_OBJECTS_INJECTION} from './hoc';
import * as filters from './tools-filter';

export default function injectToolsFilters (WrappedComponent) {
  return inject(HIDDEN_OBJECTS_INJECTION)(
    inject(({hiddenObjects}) => ({
      hiddenToolsFilter: filters.toolsFilter(hiddenObjects),
      hiddenToolGroupsFilter: filters.toolGroupsFilter(hiddenObjects),
      hiddenToolRegistriesFilter: filters.toolRegistriesFilter(hiddenObjects),
      hiddenToolsTreeFilter: filters.toolsTreeFilter(hiddenObjects)
    }))(
      observer(WrappedComponent)
    )
  );
}
