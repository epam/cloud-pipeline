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
import {message} from 'antd';
import PipelineRunSSH from '../../../../../models/pipelines/PipelineRunSSH';
import {AccessTypes} from '../../../../../models/pipelines/PipelineRunUpdateSids';
import roleModel from '../../../../../utils/roleModel';

export default function (authenticatedUserInfo, callbacks) {
  return function (service) {
    if (!authenticatedUserInfo.loaded) {
      authenticatedUserInfo.fetchIfNeededOrWait();
      return [];
    } else {
      const {userName} = authenticatedUserInfo.value;
      const {run} = service;
      const {id, runSids} = run || {};
      const {ssh} = callbacks || {};
      const [accessType] = (runSids || [])
        .filter(s => s.name === userName && s.isPrincipal)
        .map(s => s.accessType);
      if (ssh &&
        (
          accessType === AccessTypes.ssh ||
          roleModel.isOwner(run)
        )
      ) {
        const callback = async () => {
          const hide = message.loading('Fetching SSH endpoint...', 0);
          const request = new PipelineRunSSH(id);
          await request.fetch();
          hide();
          if (request.error) {
            message.error(request.error, 5);
          } else {
            ssh(request.value);
          }
        };
        return [{
          title: 'SSH',
          action: callback
        }];
      }
    }
    return [];
  };
}
