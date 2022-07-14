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
import {AccessTypes} from '../../../../../models/pipelines/PipelineRunUpdateSids';
import roleModel from '../../../../../utils/roleModel';

export default function (authenticatedUserInfo, multiZone, callbacks) {
  return function (service) {
    if (!authenticatedUserInfo.loaded) {
      authenticatedUserInfo.fetchIfNeededOrWait();
      return [];
    } else {
      const {userName} = authenticatedUserInfo.value;
      const {run, url, sameTab} = service;
      const {id, runSids} = run || {};
      const {ssh} = callbacks || {};
      const [accessType] = (runSids || [])
        .filter(s => s.name === userName && s.isPrincipal)
        .map(s => s.accessType);
      const actions = [];
      if (Object.values(url || {}).length > 1) {
        actions.push({
          title: 'OPEN',
          target: sameTab ? '_top' : '_blank',
          multiZoneUrl: url
        });
      }
      if (ssh &&
        (
          accessType === AccessTypes.ssh ||
          roleModel.isOwner(run)
        )
      ) {
        actions.push({
          title: 'SSH',
          runId: id,
          runSSH: true
        });
      }
      return actions;
    }
    return [];
  };
}
