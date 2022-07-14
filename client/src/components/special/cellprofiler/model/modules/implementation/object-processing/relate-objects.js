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

export default {
  name: 'RelateObjects',
  group: 'Object Processing',
  output: 'name|object|IF saveAsNew==true AND name!==""',
  parameters: [
    'Parent objects|object|ALIAS parent|REQUIRED',
    'Child objects|object|ALIAS child|REQUIRED',
    'Calculate per-parent means for all child measurements?|flag|true',
    'Calculate child-parent distances?|[None,Centroid,Minimum,Both]|None',
    'Do you want to save the children with parents as a new object set?|flag|false|ALIAS saveAsNew',
    'Name the output object|IF saveAsNew==true|ALIAS name|REQUIRED'
  ]
};
