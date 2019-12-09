/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import list from './template-list';
import update from './template-update';
import remove from './template-remove';

const templates = {
  list,
  Update: update,
  Remove: remove
};

export {keys, getQuotaTypeName, getQuotaTypeTargetName} from './keys';
export {rules, getRuleName} from './rules';
export {default as List} from './quota-list';
export {default as Update} from './quota-update';
export {default as Remove} from './quota-remove';
export {templates};
