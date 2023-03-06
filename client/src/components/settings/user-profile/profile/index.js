/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import classNames from 'classnames';
import {computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import {Alert} from 'antd';
import roleModel from '../../../../utils/roleModel';
import LoadingView from '../../../special/LoadingView';
import Metadata from '../../../special/metadata/Metadata';
import UserName from '../../../special/UserName';
import {METADATA_KEY as LIMIT_MOUNTS_USER_PREFERENCE}
  from '../../../special/metadata/special/limit-mounts';
import {METADATA_KEY as RUN_CAPABILITIES}
  from '../../../pipelines/launch/form/utilities/run-capabilities';
import displayDate from '../../../../utils/displayDate';
import styles from './profile.css';
import MuteEmailNotifications from '../../../special/metadata/special/mute-email-notifications';
import SshThemeSelect from '../../../special/metadata/special/ssh-theme-select';
import {withCurrentUserAttributes} from '../../../../utils/current-user-attributes';

function renderRoleName (role) {
  if (!role.predefined) {
    if (role.name && role.name.toLowerCase().indexOf('role_') === 0) {
      return role.name.substring('role_'.length);
    }
  }
  return role.name;
}

@inject('preferences')
@roleModel.authenticationInfo
@withCurrentUserAttributes()
@observer
class ProfileSettings extends React.Component {
  componentDidMount () {
    this.props.currentUserAttributes.refresh(true);
  }

  componentWillUnmount () {
    this.props.currentUserAttributes.refresh(true);
  }

  @computed
  get notificationsEnabled () {
    const {preferences} = this.props;
    if (preferences.loaded) {
      return preferences.userNotificationsEnabled;
    }
    return false;
  }

  render () {
    const {
      authenticatedUserInfo,
      preferences
    } = this.props;
    if (!authenticatedUserInfo.loaded && authenticatedUserInfo.pending) {
      return (
        <LoadingView />
      );
    }
    if (authenticatedUserInfo.error) {
      return (
        <Alert
          type="error"
          message={authenticatedUserInfo.error}
        />
      );
    }
    const userInfo = authenticatedUserInfo.value;
    const metadataKeys = preferences.metadataSystemKeys;
    return (
      <div
        className={styles.container}
      >
        <div
          className={styles.header}
        >
          <UserName
            userName={userInfo.userName}
          />
        </div>
        <table className={styles.attributes}>
          <tbody>
            <tr>
              <td className={styles.info}>User name:</td>
              <td className={styles.infoValue}>{userInfo.userName}</td>
            </tr>
            {
              userInfo.registrationDate && (
                <tr>
                  <td className={styles.info}>Registration date:</td>
                  <td className={styles.infoValue}>
                    {displayDate(userInfo.registrationDate, 'd MMMM YYYY')}
                  </td>
                </tr>
              )
            }
            {
              Object.entries(userInfo.attributes || {})
                .map(([key, value]) => (
                  <tr key={key}>
                    <td className={styles.info}>{key}:</td>
                    <td className={styles.infoValue}>{value}</td>
                  </tr>
                ))
            }
          </tbody>
        </table>
        <div
          className={
            classNames(
              'cp-divider',
              'horizontal',
              styles.divider
            )
          }
        >
          {'\u00A0'}
        </div>
        <table className={styles.attributes}>
          <tbody>
            {
              userInfo.roles && userInfo.roles.length > 0 && (
                <tr>
                  <td className={styles.info}>Roles:</td>
                  <td className={styles.infoValue}>
                    <div className={styles.roles}>
                      {
                        userInfo.roles.map(role => (
                          <span
                            key={role.id}
                            className={styles.role}
                          >
                            {renderRoleName(role)}
                          </span>
                        ))
                      }
                    </div>
                  </td>
                </tr>
              )
            }
            {
              userInfo.groups && userInfo.groups.length > 0 && (
                <tr>
                  <td
                    className={styles.info}
                  >
                    Groups:
                  </td>
                  <td
                    className={styles.infoValue}
                  >
                    <div className={styles.roles}>
                      {
                        userInfo.groups.map(group => (
                          <span
                            key={group}
                            className={styles.role}
                          >
                            {group}
                          </span>
                        ))
                      }
                    </div>
                  </td>
                </tr>
              )
            }
          </tbody>
        </table>
        <div
          className={
            classNames(
              'cp-divider',
              'horizontal',
              styles.divider
            )
          }
        >
          {'\u00A0'}
        </div>
        <Metadata
          readOnly={!userInfo.admin && !preferences.loaded}
          title="Attributes:"
          titleStyle={{fontWeight: 'bold'}}
          entityId={userInfo.id}
          entityClass="PIPELINE_USER"
          removeAllAvailable={userInfo.admin}
          restrictedKeys={userInfo.admin ? [] : metadataKeys}
          extraKeys={[
            LIMIT_MOUNTS_USER_PREFERENCE,
            RUN_CAPABILITIES,
            SshThemeSelect.metadataKey,
            this.notificationsEnabled
              ? MuteEmailNotifications.metadataKey
              : undefined
          ].filter(Boolean)}
        />
      </div>
    );
  }
}

export default ProfileSettings;
