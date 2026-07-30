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
import classNames from 'classnames';
import UsageCreditsUsers from '../../../../models/usage/UsageCreditsUsers';
import styles from './credits-counter.css';

export default class UsageCreditsCounter extends React.Component {
  state = {
    credits: undefined,
    pending: false
  };

  get isControlled () {
    return Object.prototype.hasOwnProperty.call(this.props, 'credits');
  }

  componentDidMount () {
    if (!this.isControlled) {
      this.loadCredits();
    }
  }

  componentDidUpdate (prevProps) {
    if (this.isControlled) {
      return;
    }
    const prevUserId = prevProps.user && prevProps.user.id;
    const userId = this.props.user && this.props.user.id;
    if (prevUserId !== userId) {
      this.loadCredits();
    }
  }

  loadCredits = async () => {
    const {user} = this.props;
    if (!user || !user.id) {
      return;
    }
    this.setState({pending: true});
    const request = new UsageCreditsUsers();
    await request.send({
      userIds: [user.id],
      page: 1,
      pageSize: 1
    });
    if (request.loaded && request.value && request.value.elements) {
      const [element] = request.value.elements;
      this.setState({
        credits: element && element.currentValue !== undefined
          ? element.currentValue
          : undefined,
        pending: false
      });
      return;
    }
    this.setState({pending: false});
  };

  render () {
    const {
      label = 'Usage credits:',
      className = {},
      style = {}
    } = this.props;
    const credits = this.isControlled
      ? this.props.credits
      : this.state.credits;
    const pending = this.isControlled
      ? false
      : this.state.pending;
    if (pending || credits === undefined) {
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
