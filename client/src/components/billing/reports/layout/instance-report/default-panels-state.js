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

export default [
  {'w': 12, 'h': 12, 'x': 0, 'y': 0, 'i': Panels.summary, 'moved': false, 'static': false},
  {'w': 12, 'h': 12, 'x': 12, 'y': 0, 'i': Panels.instances, 'moved': false, 'static': false},
  {'w': 12, 'h': 12, 'x': 0, 'y': 12, 'i': Panels.pipelines, 'moved': false, 'static': false},
  {'w': 12, 'h': 12, 'x': 12, 'y': 12, 'i': Panels.tools, 'moved': false, 'static': false}
];
