/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import React, {Component} from 'react';
import {computed} from 'mobx';
import PropTypes from 'prop-types';
import {Link} from 'react-router';
import styles from './TaskLink.css';
import StatusIcon from '../../../special/run-status-icon';
import displayDate from '../../../../utils/displayDate';
import moment from 'moment';

export class TaskLink extends Component {
  static propTypes = {
    to: PropTypes.string,
    task: PropTypes.shape({
      name: PropTypes.string,
      status: PropTypes.string
    }),
    location: PropTypes.shape({
      pathinfo: PropTypes.string
    }),
    timings: PropTypes.bool
  };

  @computed
  get runningFor () {
    return moment.utc(this.props.task.started).fromNow(true);
  }

  @computed
  get startDelay () {
    return `${moment.utc(this.props.task.started)
      .diff(moment.utc(this.props.task.created), 'minutes', true).toFixed(2)} min`;
  }

  @computed
  get waitingFor () {
    return moment.utc(this.props.task.created).fromNow(true);
  }

  @computed
  get runningTime () {
    return `${moment.utc(this.props.task.finished)
      .diff(moment.utc(this.props.task.started), 'minutes', true).toFixed(2)} min`;
  }

  render () {
    const {to, task: {name, status}, location: {pathinfo}} = this.props;
    const taskNameClass = 'default';
    const infos = [];
    if (this.props.timings) {
      if (this.props.task.created) {
        infos.push(<div key="scheduled" className={styles.timeInfo}>
          <span><b>Scheduled:</b> {displayDate(this.props.task.created)}</span><br /></div>);
      }
      if (this.props.task.started) {
        infos.push(
          <div key="started" className={styles.timeInfo}>
            <span>
              <b>Started:</b> {displayDate(this.props.task.started)} ({this.startDelay})
            </span>
            <br />
          </div>
        );
        if (status !== 'RUNNING') {
          infos.push(
            <div key="finished" className={styles.timeInfo}>
              <span>
                <b>Finished:</b> {displayDate(this.props.task.finished)} ({this.runningTime})
              </span>
              <br />
            </div>
          );
        } else {
          infos.push(
            <div key="finished" className={styles.timeInfo}>
              <span>
                <b>Running for:</b> {this.runningFor}
              </span>
              <br />
            </div>
          );
        }
      } else {
        infos.push(
          <div key="started" className={styles.timeInfo}>
            <span>
              <b>Waiting for:</b> {this.waitingFor}
            </span>
            <br />
          </div>
        );
      }
    }

    if (to === pathinfo) {
      return (
        <div>
          <StatusIcon status={status} small displayTooltip={false} />
          <span>
            <b>{name}</b>
          </span>
          <br />
          {infos}
        </div>
      );
    }

    return (
      <Link to={to}>
        <StatusIcon status={status} small />
        <span className={styles[taskNameClass]}>
          <b>{name}</b>
        </span>
        <br />
        {infos}
      </Link>
    );
  }
}
