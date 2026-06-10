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
import {makeObservable} from 'mobx';
import {inject, observer} from 'mobx-react';
import GeneralInfoTab from './general-info';
import GPUInfoHoc from './gpu-info-hoc';
import SubSettings from '../../settings/sub-settings';

@inject('preferences')
@observer
class ClusterNodeMonitor extends React.Component {
  constructor(props) {
    super(props);
    makeObservable(this, {});
  }

  render() {
    const {node, nodeName, chartsData, preferences, router} = this.props;
    const instanceType = node.value?.labels?.cloud_ins_type;
    const tabs = [
      {
        key: 'general',
        title: 'General statistics',
        render: () => (
          <GeneralInfoTab
            chartsData={chartsData}
            node={node}
            nodeName={nodeName}
            preferences={preferences}
            router={router}
          />
        ),
      },
      {
        key: 'gpu',
        title: 'GPU statistics',
        render: () => (
          <GPUInfoHoc
            nodeName={nodeName}
            chartsData={chartsData}
            node={node}
            instanceType={instanceType}
            router={router}
          />
        ),
      },
    ];
    return (
      <div style={{display: 'flex', flex: 1, height: 300, overflow: 'hidden'}}>
        <SubSettings sections={tabs} />
      </div>
    );
  }
}

export default ClusterNodeMonitor;
