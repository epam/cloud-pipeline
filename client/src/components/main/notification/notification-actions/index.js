/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import {computed, observable} from 'mobx';
import {
  Dropdown,
  Icon,
  Menu,
  message
} from 'antd';
import PipelineRunInfo from '../../../../models/pipelines/PipelineRunInfo';
import styles from './notification-actions.css';
import {ACTIONS, ENTITY_CLASSES, NOTIFICATION_TYPES} from './actions';

@inject('preferences')
@observer
class NotificationActions extends React.Component {
  state = {
    visible: false,
    pending: false,
    entityRequestPending: false
  };

  @observable
  entityInfoRequest;

  groupedActions = {
    [NOTIFICATION_TYPES.IDLE_RUN]: [
      ACTIONS.viewRun,
      ACTIONS.pauseRun,
      ACTIONS.stopRun
    ],
    [NOTIFICATION_TYPES.IDLE_RUN_PAUSED]: [
      ACTIONS.viewRun,
      ACTIONS.resumeRun,
      ACTIONS.terminateRun
    ],
    [NOTIFICATION_TYPES.IDLE_RUN_STOPPED]: [
      ACTIONS.viewRun
    ],
    [NOTIFICATION_TYPES.HIGH_CONSUMED_RESOURCES]: [
      ACTIONS.viewRun
    ],
    [NOTIFICATION_TYPES.LONG_INIT]: [
      ACTIONS.viewRun,
      ACTIONS.terminateRun
    ],
    [NOTIFICATION_TYPES.LONG_PAUSED]: [
      ACTIONS.viewRun,
      ACTIONS.resumeRun,
      ACTIONS.terminateRun
    ],
    [NOTIFICATION_TYPES.LONG_PAUSED_STOPPED]: [
      ACTIONS.viewRun
    ],
    [NOTIFICATION_TYPES.LONG_RUNNING]: [
      ACTIONS.viewRun,
      ACTIONS.pauseRun,
      ACTIONS.stopRun
    ],
    [NOTIFICATION_TYPES.LONG_STATUS]: [
      ACTIONS.viewRun
    ],
    [NOTIFICATION_TYPES.PIPELINE_RUN_STATUS]: [
      ACTIONS.viewRun
    ],
    [NOTIFICATION_TYPES.DATASTORAGE_LIFECYCLE_RESTORE_ACTION]: [
      ACTIONS.openDatastorage
    ],
    [NOTIFICATION_TYPES.STORAGE_QUOTA_EXCEEDING]: [
      ACTIONS.openDatastorage
    ],
    [NOTIFICATION_TYPES.DATASTORAGE_LIFECYCLE_ACTION]: [
      ACTIONS.openDatastorage,
      ACTIONS.postponeLifecycleRule
    ],
    [NOTIFICATION_TYPES.FULL_NODE_POOL]: [
      ACTIONS.openPoolsUsage
    ]
  };

  @computed
  get actions () {
    const {preferences} = this.props;
    const {type} = this.notificationDetails;
    const actions = this.groupedActions[type] || [];
    const entityValue = this.entityInfoRequest && this.entityInfoRequest.loaded
      ? this.entityInfoRequest.value
      : undefined;
    return actions
      .filter(Boolean)
      .filter(({available}) => available(entityValue, preferences));
  }

  get notificationDetails () {
    const {notification = {}} = this.props;
    const details = (notification.resources || [])[0] || {};
    return {
      ...details,
      type: notification.type
    };
  }

  get showActionsControl () {
    const {type} = this.notificationDetails;
    return type && type !== NOTIFICATION_TYPES.INACTIVE_USERS &&
      type !== NOTIFICATION_TYPES.LDAP_BLOCKED_POSTPONED_USERS &&
      type !== NOTIFICATION_TYPES.LDAP_BLOCKED_USERS;
  }

  fetchEntityInfo = () => {
    return new Promise(resolve => {
      const {entityClass, entityId} = this.notificationDetails;
      switch (entityClass) {
        case ENTITY_CLASSES.RUN:
          this.entityInfoRequest = new PipelineRunInfo(entityId);
          break;
      }
      if (!this.entityInfoRequest) {
        resolve();
        return;
      }
      this.setState({
        entityRequestPending: true
      }, async () => {
        await this.entityInfoRequest.fetch();
        if (this.entityInfoRequest.error) {
          message.error(this.entityInfoRequest.error);
        }
        this.setState({
          entityRequestPending: false
        }, () => resolve());
      });
    });
  };

  showMenu = () => {
    this.setState({visible: true});
  };

  hideMenu = () => {
    this.setState({visible: false});
  };

  handleVisibleChange = async (visible) => {
    if (!visible) {
      return this.hideMenu();
    }
    await this.fetchEntityInfo();
    this.showMenu();
  };

  render () {
    const {
      pending,
      notification,
      style
    } = this.props;
    const {visible, entityRequestPending} = this.state;
    const menu = (
      <Menu
        selectedKeys={[]}
        style={{cursor: 'default', minWidth: '120px'}}
        onClick={({key}) => {
          const action = this.actions.find(action => action.key === key);
          action && action.actionFn({
            notification,
            entity: (this.entityInfoRequest || {}).value,
            router: this.props.router,
            callback: this.hideMenu
          });
        }}
      >
        {this.actions.length > 0 ? (
          this.actions.map(action => (
            <Menu.Item
              key={action.key}
            >
              {action.key}
            </Menu.Item>
          ))
        ) : (
          <Menu.Item key="empty" disabled>No actions available</Menu.Item>
        )}
      </Menu>
    );
    return (
      <div>
        {this.showActionsControl ? (
          <Dropdown
            overlay={menu}
            trigger={['click']}
            onVisibleChange={this.handleVisibleChange}
            visible={visible}
            disabled={pending}
            onClick={e => e.stopPropagation()}
          >
            <Icon
              type={entityRequestPending ? 'loading' : 'setting'}
              className={styles.controlsIcon}
              style={style}
            />
          </Dropdown>
        ) : null}
      </div>
    );
  }
}

NotificationActions.propTypes = {
  style: PropTypes.object,
  notification: PropTypes.object,
  router: PropTypes.object
};

export default NotificationActions;
