/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Alert} from 'antd';
import Cluster from '../Cluster';
import {MACHINE_TYPES} from '../../../models/cluster/ClusterNodes';
import roleModel from '../../../utils/roleModel';
import {observer} from 'mobx-react';
import LoadingView from '../../special/LoadingView';
import {isAdmin} from '../utilities/access-permissinos';

@roleModel.authenticationInfo
@observer
export default class CloudNodes extends React.Component {
  render () {
    const {authenticatedUserInfo} = this.props;
    const {pending, loaded, value, error: loadError} = authenticatedUserInfo;
    if (pending && !loaded) {
      return (
        <div style={{width: '100%', height: '100%'}}>
          <LoadingView />
        </div>
      );
    }
    const error = (() => {
      if (loadError) {
        return `Access is denied: ${loadError}`;
      }
      if (!value) {
        return `Access is denied: error fetching user info`;
      }
      if (!isAdmin(value)) {
        return `Access is denied`;
      }
      return undefined;
    })();
    if (error) {
      return (
        <div style={{width: '100%', height: '100%'}}>
          <Alert title={error} type="error" />
        </div>
      );
    }
    return (
      <Cluster
        {...this.props}
        machineType={MACHINE_TYPES.cloud}
        highlightCloudNodes={false}
        title="Cloud nodes"
      />
    );
  }
};
