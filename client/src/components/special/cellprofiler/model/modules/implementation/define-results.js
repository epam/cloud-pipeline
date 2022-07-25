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
/* eslint-disable max-len */

import {
  DefineResultsParameter,
  defineResultsSubModules
} from '../../parameters/define-results';

const DefineResultsModuleName = 'Results';

const defineResults = {
  name: DefineResultsModuleName,
  predefined: true,
  title: 'Define Results',
  hidden: true,
  composed: true,
  parameters: [DefineResultsParameter],
  subModules: defineResultsSubModules
};

const defineResultsInternal = {
  name: 'DefineResults',
  group: 'System',
  hidden: true,
  parameters: [
    'specs|custom|ALIAS specs',
    'grouping|custom|ALIAS grouping'
  ]
};

export {
  defineResults,
  defineResultsInternal,
  DefineResultsModuleName
};
