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

import React from 'react';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {Alert} from 'antd';
import dataStorages from '../../../models/dataStorage/DataStorages';
import {CP_CAP_LIMIT_MOUNTS} from '../../pipelines/launch/form/utilities/parameters';

function getSensitiveBuckets (parameters, dataStoragesStore) {
  if (!parameters || !parameters.hasOwnProperty(CP_CAP_LIMIT_MOUNTS) || !dataStoragesStore) {
    return [];
  }
  if (!dataStoragesStore.loaded) {
    return [];
  }
  const {value} = parameters[CP_CAP_LIMIT_MOUNTS];
  if (!value || /^none$/i.test(value)) {
    return [];
  }
  const ids = (value || '').split(',').map(id => +id);
  return (dataStoragesStore.value || [])
    .filter(storage => ids.indexOf(+(storage.id)) >= 0 && storage.sensitive);
}

function SensitiveBucketsWarning (
  {
    dataStorages,
    parameters,
    message,
    showIcon = true,
    style = {}
  }
) {
  const sensitiveStorages = getSensitiveBuckets(parameters, dataStorages);
  if (!sensitiveStorages.length) {
    return null;
  }
  return (
    <Alert
      showIcon={showIcon}
      type="warning"
      style={style}
      message={message || (
        <div>
          <div>You are going to launch a job with <b>sensitive storages</b>.</div>
          <div>
            This will apply a number of restrictions for the job: no Internet access,
            all the storages will be available in a read-only mode,
            you won't be able to extract the data from the running job and other.
          </div>
        </div>
      )}
    />
  );
}

SensitiveBucketsWarning.propTypes = {
  parameters: PropTypes.object,
  style: PropTypes.object
};

export default inject(() => ({dataStorages}))(observer(SensitiveBucketsWarning));
