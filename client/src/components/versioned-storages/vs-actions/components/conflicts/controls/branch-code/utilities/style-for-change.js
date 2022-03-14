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

import ChangeType from '../../../utilities/changes/types';
import ChangeStatuses from '../../../utilities/changes/statuses';

export default function getStyleForChange (change, renderingConfig, hidden = false) {
  const {type, status} = change || {};
  if (hidden) {
    return {
      backgroundColor: 'transparent',
      borderColor: 'transparent'
    };
  }
  let config;
  switch (type) {
    case ChangeType.edition: config = renderingConfig.edition; break;
    case ChangeType.conflict: config = renderingConfig.conflict; break;
    case ChangeType.deletion: config = renderingConfig.deletion; break;
    case ChangeType.insertion: config = renderingConfig.insertion; break;
    default:
      break;
  }
  const applied = status !== ChangeStatuses.prepared;
  if (config) {
    return {
      backgroundColor: applied ? 'transparent' : (config.background || config.color),
      borderColor: applied ? config.applied : config.color
    };
  }
  return {
    backgroundColor: 'transparent',
    borderColor: 'transparent'
  };
}
