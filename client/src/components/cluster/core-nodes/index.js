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
import CoreNodesTable from './core-nodes-table';
import CoreServicesTable from './core-services-table';
import SubSettings from '../../settings/sub-settings';
import ProxyState from './proxy-state';
import whoAmI from '../../../models/user/WhoAmI';
import {isAdmin, isClusterReader} from '../utilities/access-permissinos';
import LoadingView from '../../special/LoadingView';

const allowClusterReaders = false;

export default class CoreNodes extends React.Component {
  state = {
    tabs: [],
    pending: !whoAmI.loaded,
    error: undefined
  };

  componentDidMount () {
    this.buildTabs();
  }

  buildTabs = () => {
    (async () => {
      try {
        this.setState({pending: true, error: undefined, tabs: []});
        await whoAmI.fetchIfNeededOrWait();
        if (whoAmI.error) {
          throw new Error(`Access is denied: ${whoAmI.error}`);
        }
        if (!whoAmI.loaded || !whoAmI.value) {
          throw new Error('Access is denied: error fetching user info');
        }
        const userIsAdmin = isAdmin(whoAmI.value);
        const userIsClusterReader = allowClusterReaders ? isClusterReader(whoAmI.value) : false;
        if (!userIsAdmin && !userIsClusterReader) {
          throw new Error('Access is denied');
        }
        const tabs = (() => {
          if (userIsAdmin) {
            return [
              {
                key: 'nodes',
                title: 'Core nodes',
                render: () => <CoreNodesTable router={this.props.router} />
              },
              {
                key: 'services',
                title: 'Core services',
                render: () => <CoreServicesTable />
              },
              {
                key: 'proxy-state',
                title: 'Proxy state',
                render: () => <ProxyState />
              }
            ];
          }
          return [
            {
              key: 'nodes',
              title: 'Core nodes',
              render: () => <CoreNodesTable router={this.props.router} />
            }
          ];
        })();
        this.setState({error: undefined, pending: false, tabs});
      } catch (error) {
        this.setState({error: error.message, pending: false, tabs: []});
      }
    })();
  }

  render () {
    const {
      pending,
      error,
      tabs
    } = this.state;
    if (pending) {
      return (
        <div style={{display: 'flex', flex: 1}}>
          <LoadingView />
        </div>
      );
    }
    if (error) {
      return (
        <div style={{display: 'flex', flex: 1}}>
          <div style={{width: '100%'}}>
            <Alert message={error} type="error" style={{width: '100%'}} />
          </div>
        </div>
      );
    }
    return (
      <div style={{display: 'flex', flex: 1}}>
        <SubSettings
          sections={tabs}
          showSingleSection={false}
        />
      </div>
    );
  }
};
