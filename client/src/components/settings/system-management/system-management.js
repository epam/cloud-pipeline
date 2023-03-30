/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Modal} from 'antd';

import SystemLogs from './system-logs';
import NATGetaway from './nat-getaway-configuration/nat-getaway-configuration';
import SystemJobs from './system-jobs';
import SubSettings from '../sub-settings';

export default class SystemManagement extends React.Component {
  state={
    modified: false,
    changesCanBeSkipped: false
  }

  componentDidMount () {
    const {route, router} = this.props;
    if (route && router) {
      router.setRouteLeaveHook(route, this.checkModifiedBeforeLeave);
    }
  }

  componentWillUnmount () {
    this.resetChangesStateTimeout && clearTimeout(this.resetChangesStateTimeout);
  }

  handleModified = (modified) => {
    if (this.state.modified !== modified) {
      this.setState({modified});
    }
  }

  confirmChangeURL = () => {
    return new Promise((resolve) => {
      if (this.state.modified) {
        Modal.confirm({
          title: 'You have unsaved changes. Continue?',
          style: {
            wordWrap: 'break-word'
          },
          onOk () {
            resolve(true);
          },
          onCancel () {
            resolve(false);
          },
          okText: 'Yes',
          cancelText: 'No'
        });
      } else {
        resolve(true);
      }
    });
  };

  checkModifiedBeforeLeave = (nextLocation) => {
    const {router} = this.props;
    const {changesCanBeSkipped, modified} = this.state;
    const resetChangesCanBeSkipped = () => {
      this.resetChangesStateTimeout = setTimeout(
        () => this.setState && this.setState({changesCanBeSkipped: false}),
        0
      );
    };
    const makeTransition = () => {
      this.setState({changesCanBeSkipped: true},
        () => {
          router.push(nextLocation);
          resetChangesCanBeSkipped();
        }
      );
    };
    if (modified && !changesCanBeSkipped) {
      this.confirmChangeURL()
        .then(confirmed => confirmed ? makeTransition() : undefined);
      return false;
    }
  };

  render () {
    return (
      <SubSettings
        sections={[
          {
            key: 'logs',
            title: 'LOGS',
            default: true,
            render: () => (<SystemLogs />)
          },
          {
            key: 'nat',
            title: 'NAT GATEWAY',
            render: () => (<NATGetaway handleModified={this.handleModified} />)
          },
          {
            key: 'jobs',
            title: 'SYSTEM JOBS',
            render: () => (<SystemJobs router={this.props.router} />)
          }
        ]}
        router={this.props.router}
        canNavigate={this.confirmChangeURL}
        root="system"
      />
    );
  }
}
