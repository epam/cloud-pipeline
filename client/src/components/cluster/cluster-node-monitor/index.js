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
import {inject, observer} from 'mobx-react';
import GeneralInfoTab from './general-info';
import SubSettings from '../../settings/sub-settings';

@inject('preferences')
@observer
class ClusterNodeMonitor extends React.Component {
  render () {
    const {node, chartsData, nodeName} = this.props;
    const tabs = [
      {
        key: 'general',
        title: 'General statistics',
        render: () => <GeneralInfoTab
          chartsData={chartsData}
          node={node}
          nodeName={nodeName}
          preferences={this.props.preferences}
        />
      },
      {
        key: 'gpu',
        title: 'GPU statistics',
        render: () => <div>GPU statistics</div>
      }
    ];
    return (
      <div style={{display: 'flex', flex: 1, height: 300, overflow: 'hidden'}}>
        <SubSettings
          sections={tabs}
        />
      </div>
    );
  }
}

export default ClusterNodeMonitor;
