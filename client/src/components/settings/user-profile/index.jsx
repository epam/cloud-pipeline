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
import SubSettings from '../sub-settings';
import ProfileSettings from './profile';
import AppearanceSettings, {MANAGEMENT_SECTION} from './appearance';
import roleModel from '../../../utils/roleModel';
import UserInfoSummary from '../forms/EditUserRolesDialog/UserInfoSummary';

@roleModel.authenticationInfo
@observer
export default class UserProfile extends React.Component {
  get user() {
    if (this.props.authenticatedUserInfo && this.props.authenticatedUserInfo.loaded) {
      return this.props.authenticatedUserInfo.value;
    }
    return undefined;
  }

  render() {
    const {router} = this.props;
    const user = this.user;
    const sections = [
      {
        key: 'profile',
        title: 'PROFILE',
        default: true,
        render: () => <ProfileSettings />,
      },
      {
        key: 'appearance',
        title: 'APPEARANCE',
        render: ({router: sectionRouter, sub} = {}) => (
          <AppearanceSettings
            router={sectionRouter}
            management={MANAGEMENT_SECTION.toLowerCase() === (sub || '').toLowerCase()}
          />
        ),
      },
      {
        key: 'statistics',
        title: 'STATISTICS',
        render: () => <UserInfoSummary user={user} />,
      },
    ];
    return <SubSettings sections={sections} router={router} root="profile" />;
  }
}
