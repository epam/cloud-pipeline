/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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
import {observer, inject} from 'mobx-react';
import classNames from 'classnames';
import User from '../../../../models/user/User';
import styles from './credits-counter.css';

function getCredits (user) {
  const {usageCredits} = user || {};
  return usageCredits ? usageCredits.currentValue : undefined;
}

@inject('authenticatedUserInfo')
@observer
export default class UsageCreditsCounter extends React.Component {
  state = {
    credits: undefined,
    pending: false
  };

  get user () {
    const {user, authenticatedUserInfo} = this.props;
    if (user) {
      return user;
    }
    return authenticatedUserInfo && authenticatedUserInfo.loaded
      ? authenticatedUserInfo.value
      : undefined;
  }

  get credits () {
    const credits = getCredits(this.user);
    return credits === undefined ? this.state.credits : credits;
  }

  componentDidMount () {
    this.loadCreditsIfNeeded();
  }

  componentDidUpdate (prevProps) {
    const prevUserId = prevProps.user ? prevProps.user.id : undefined;
    const userId = this.props.user ? this.props.user.id : undefined;
    if (prevUserId !== userId) {
      this.loadCreditsIfNeeded();
    }
  }

  // `/users` and `/whoami` conditionally include the credits,
  // so a request is only needed for a user passed in without them
  loadCreditsIfNeeded = () => {
    const {user} = this.props;
    if (!user || user.id === undefined || user.id === null) {
      return;
    }
    if (getCredits(user) === undefined) {
      this.loadCredits(user.id);
    }
  };

  loadCredits = async (userId) => {
    this.setState({credits: undefined, pending: true});
    const request = new User(userId, true);
    await request.fetch();
    const {user} = this.props;
    if (!user || user.id !== userId) {
      return;
    }
    this.setState({
      credits: request.loaded ? getCredits(request.value) : undefined,
      pending: false
    });
  };

  render () {
    const {
      label = 'Usage credits:',
      className = {},
      style = {}
    } = this.props;
    const {credits} = this;
    if (this.state.pending || credits === undefined) {
      return null;
    }
    return (
      <div
        className={classNames(styles.container, className.container)}
        style={style.container}
      >
        <b
          className={classNames(styles.label, className.label)}
          style={style.label}
        >
          {label}
        </b>
        <span
          className={classNames(styles.value, className.value)}
          style={style.value}
        >
          {credits}
        </span>
      </div>
    );
  }
}
