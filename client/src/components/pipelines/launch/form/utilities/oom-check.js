/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import React from 'react';
import {observer} from 'mobx-react';
import {Alert} from 'antd';

function OOMCheck (
  {
    instance,
    limitMounts,
    dataStorages,
    preferences,
    style
  }
) {
  if (
    !preferences ||
    !preferences.loaded ||
    !preferences.storageMountsPerGBRatio ||
    !instance ||
    !instance.memory ||
    Number.isNaN(instance.memory)
  ) {
    return null;
  }
  const allNonSensitive = !limitMounts;
  const ids = limitMounts && !/^none$/i.test(limitMounts)
    ? (new Set(limitMounts.split(',').map(id => +id)))
    : new Set();
  const storages = (dataStorages || [])
    .filter(storage => (allNonSensitive && !storage.sensitive) || ids.has(storage.id))
    .filter(storage => !/^nfs$/i.test(storage.type))
    .length;
  if (preferences.storageMountsPerGBRatio * instance.memory <= storages) {
    return (
      <Alert
        type="warning"
        style={style}
        showIcon
        message={(
          <div>
            <div style={{fontWeight: 'bold'}}>
              A large number of the object data storages ({storages})
              are going to be mounted for this job.
            </div>
            <div>
              This may cause slow performance or even <b>Out Of Memory</b> errors.
              Please increase the node type to a larger one or use the <b>Limit mounts</b> option
              to reduce a number of the object storages.
            </div>
            <div>
              Note: this recommendation is only for the object storages,
              file storages do not introduce this limitation.
            </div>
          </div>
        )}
      />
    );
  }
  return null;
}

export default observer(OOMCheck);
