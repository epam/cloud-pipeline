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
import CoreNodesTable from './core-nodes-table';
import CoreServicesTable from './core-services-table';
import SubSettings from '../../settings/sub-settings';

export default class CoreNodes extends React.Component {
  state = {
    activeTabKey: 'nodes'
  };

  tabs = [
    {
      key: 'nodes',
      title: 'Core nodes',
      render: () => <CoreNodesTable router={this.props.router} />
    },
    {
      key: 'services',
      title: 'Core services',
      render: () => <CoreServicesTable />
    }
  ];

  onChangeTab = (key) => this.setState({activeTabKey: key});

  render () {
    return (
      <div style={{display: 'flex', flex: 1}}>
        <SubSettings
          sections={this.tabs}
        />
      </div>
    );
  }
};
