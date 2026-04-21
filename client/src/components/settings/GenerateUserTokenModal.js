/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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
import {DatePicker, Input, message, Modal} from 'antd';
import moment from 'moment-timezone';
import UserToken from '../../models/user/UserToken';

@inject('authenticatedUserInfo', 'preferences')
@observer
export default class GenerateUserTokenModal extends React.Component {
  static propTypes = {
    visible: PropTypes.bool,
    onCancel: PropTypes.func,
    onGenerated: PropTypes.func
  };

  state = {
    validTill: undefined,
    name: '',
    confirmLoading: false
  };

  componentDidMount () {
    if (this.props.visible) {
      this.resetForm();
    }
  }

  componentDidUpdate (prevProps) {
    if (this.props.visible && !prevProps.visible) {
      this.resetForm();
    }
  }

  get isAdmin () {
    const {authenticatedUserInfo} = this.props;
    if (authenticatedUserInfo && authenticatedUserInfo.loaded) {
      return authenticatedUserInfo.value.admin;
    }
    return false;
  }

  get jwtTokenExpirationUserLimitSeconds () {
    if (this.isAdmin) {
      return 0;
    }
    const {preferences} = this.props;
    return preferences.launchJWTTokenExpirationUserLimit;
  }

  get jwtTokenDateTo () {
    const seconds = this.jwtTokenExpirationUserLimitSeconds;
    if (seconds > 0) {
      return moment().add(seconds, 'seconds').endOf('day');
    }
    return undefined;
  }

  disabledDate = (date) => {
    if (!date) {
      return false;
    }
    if (date.isBefore(moment().startOf('day'))) {
      return true;
    }
    const {jwtTokenDateTo} = this;
    if (jwtTokenDateTo && date.isAfter(jwtTokenDateTo)) {
      return true;
    }
    return false;
  };

  resetForm = () => {
    this.setState({
      validTill: undefined,
      name: '',
      confirmLoading: false
    });
    (async () => {
      try {
        const {preferences} = this.props;
        await preferences.fetchIfNeededOrWait();
      } catch {
        // noop
      }
      if (!this.props.visible) {
        return;
      }
      this.setState({
        validTill: this.jwtTokenDateTo || moment().add(1, 'M')
      });
    })();
  };

  onValidTillChanged = (date) => {
    if (date && date.isBefore(moment().startOf('day'))) {
      message.info('\'Valid till\' date should not be in past');
      return;
    }
    this.setState({validTill: date});
  };

  onNameChanged = (event) => {
    this.setState({name: event.target.value});
  };

  handleOk = async () => {
    const {validTill, name} = this.state;
    const {onGenerated} = this.props;
    if (!validTill) {
      return;
    }
    this.setState({confirmLoading: true});
    try {
      const expiration = validTill.clone().endOf('day');
      let seconds = expiration.diff(moment(), 'seconds');
      const limit = this.jwtTokenExpirationUserLimitSeconds;
      if (limit > 0) {
        seconds = Math.min(seconds, limit);
      }
      const trimmedName = (name || '').trim();
      const request = new UserToken(seconds, trimmedName || undefined);
      await request.fetch();
      if (request.error) {
        throw new Error(request.error);
      }
      const token = request.value.token;
      if (typeof onGenerated === 'function') {
        onGenerated(token, {validTill, name: trimmedName});
      }
    } catch (error) {
      message.error(error.message);
    } finally {
      this.setState({confirmLoading: false});
    }
  };

  render () {
    const {visible, onCancel} = this.props;
    const {validTill, name, confirmLoading} = this.state;
    return (
      <Modal
        closable={!confirmLoading}
        confirmLoading={confirmLoading}
        maskClosable={!confirmLoading}
        onCancel={onCancel}
        onOk={this.handleOk}
        okButtonProps={{disabled: !validTill}}
        title="Generate access key"
        visible={visible}
        okText="Generate"
      >
        <div style={{display: 'flex', alignItems: 'flex-end'}}>
          <div style={{flex: '0 0 auto', marginRight: 12}}>
            <div style={{marginBottom: 4}}><b>Valid till:</b></div>
            <DatePicker
              className="generate-user-token-valid-till"
              allowClear={false}
              disabledDate={this.disabledDate}
              onChange={this.onValidTillChanged}
              value={validTill}
            />
          </div>
          <div style={{flex: '1 1 auto'}}>
            <div style={{marginBottom: 4}}><b>Name:</b></div>
            <Input
              className="generate-user-token-name"
              onChange={this.onNameChanged}
              placeholder="Name (optional)"
              value={name}
            />
          </div>
        </div>
      </Modal>
    );
  }
}
