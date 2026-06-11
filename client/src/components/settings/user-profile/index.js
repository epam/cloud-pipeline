/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {computed} from 'mobx';
import {Modal} from 'antd';
import SubSettings from '../sub-settings';
import ProfileSettings from './profile';
import AppearanceSettings, {MANAGEMENT_SECTION} from './appearance';
import LaunchProfilesSettings from './launch-profiles';
import roleModel from '../../../utils/roleModel';
import UserInfoSummary from '../forms/EditUserRolesDialog/UserInfoSummary';

@roleModel.authenticationInfo
@observer
export default class UserProfile extends React.Component {
  state = {
    launchProfilesModified: false,
    changesCanBeSkipped: false
  };

  @computed
  get user () {
    if (
      this.props.authenticatedUserInfo &&
      this.props.authenticatedUserInfo.loaded
    ) {
      return this.props.authenticatedUserInfo.value;
    }
    return undefined;
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

  checkModifiedBeforeLeave = (nextLocation) => {
    const {router} = this.props;
    const {changesCanBeSkipped, launchProfilesModified} = this.state;
    const resetChangesCanBeSkipped = () => {
      this.resetChangesStateTimeout = setTimeout(
        () => this.setState && this.setState({changesCanBeSkipped: false}),
        0
      );
    };
    const makeTransition = () => {
      this.setState({changesCanBeSkipped: true}, () => {
        router.push(nextLocation);
        resetChangesCanBeSkipped();
      });
    };
    if (launchProfilesModified && !changesCanBeSkipped) {
      this.confirmChanges()
        .then(confirmed => confirmed ? makeTransition() : undefined);
      return false;
    }
  };

  confirmChanges = () => new Promise((resolve) => {
    Modal.confirm({
      title: 'Changes will not be saved. Continue?',
      onOk () { resolve(true); },
      onCancel () { resolve(false); },
      okText: 'Yes',
      cancelText: 'No'
    });
  });

  canNavigateSections = () => {
    const {launchProfilesModified} = this.state;
    if (!launchProfilesModified) return Promise.resolve(true);
    return this.confirmChanges();
  };

  getSections = () => {
    const sections = [];
    sections.push({
      key: 'profile',
      title: 'PROFILE',
      default: true,
      render: () => (<ProfileSettings />)
    });
    sections.push({
      key: 'appearance',
      title: 'APPEARANCE',
      render: ({router, sub} = {}) => (
        <AppearanceSettings
          router={router}
          management={MANAGEMENT_SECTION.toLowerCase() === (sub || '').toLowerCase()}
        />
      )
    });
    sections.push({
      key: 'launch-profiles',
      title: 'LAUNCH PROFILES',
      render: () => (
        <LaunchProfilesSettings
          onModified={(modified) => this.setState({launchProfilesModified: modified})}
        />
      )
    });
    sections.push({
      key: 'statistics',
      title: 'STATISTICS',
      render: () => (
        <UserInfoSummary
          user={this.user}
        />
      )
    });
    return sections;
  };

  render () {
    const {router} = this.props;
    return (
      <SubSettings
        sections={this.getSections()}
        router={router}
        root="profile"
        canNavigate={this.canNavigateSections}
      />
    );
  }
}
