/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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
import classNames from 'classnames';
import displayDuration from '../../../../../utils/displayDuration';
import StatusIcon from '../../../../special/run-status-icon';
import highlightText from '../../../../special/highlightText';
import displayDate from '../../../../../utils/displayDate';
import styles from './run-logs.css';

class RunTask extends Component {
  @computed
  get runningFor () {
    return displayDuration(this.props.task.started);
  }

  @computed
  get startDelay () {
    return displayDuration(
      this.props.task.created || this.props.task.started,
      this.props.task.started
    );
  }

  @computed
  get waitingFor () {
    return displayDuration(this.props.task.created);
  }

  @computed
  get runningTime () {
    return displayDuration(this.props.task.started, this.props.task.finished);
  }

  render () {
    const {
      task,
      searchText,
      active,
      className,
      style,
      onTaskClick
    } = this.props;
    const {name, status} = task;
    const onClick = (event) => {
      event.stopPropagation();
      event.preventDefault();
      if (onTaskClick) {
        onTaskClick(task);
      }
    };
    const infos = [];
    if (this.props.timings) {
      if (this.props.task.created) {
        infos.push(<div key="scheduled" className={styles.timeInfo}>
          <span><b>Scheduled:</b> {displayDate(this.props.task.created)}</span></div>);
      }
      if (this.props.task.started) {
        infos.push(
          <div key="started" className={styles.timeInfo}>
            <span>
              <b>Started:</b> {displayDate(this.props.task.started)} ({this.startDelay})
            </span>
          </div>
        );
        if (status !== 'RUNNING') {
          infos.push(
            <div key="finished" className={styles.timeInfo}>
              <span>
                <b>Finished:</b> {displayDate(this.props.task.finished)} ({this.runningTime})
              </span>
            </div>
          );
        } else {
          infos.push(
            <div key="finished" className={styles.timeInfo}>
              <span>
                <b>Running for:</b> {this.runningFor}
              </span>
            </div>
          );
        }
      } else {
        infos.push(
          <div key="started" className={styles.timeInfo}>
            <span>
              <b>Waiting for:</b> {this.waitingFor}
            </span>
          </div>
        );
      }
    }

    return (
      <div
        className={classNames(
          className,
          styles.runTask,
          {'cp-primary': active, [styles.active]: active},
          'cp-divider',
          'light',
          'bottom'
        )}
        style={style}
        onClick={onClick}
      >
        <div className={styles.runTaskTitle}>
          <StatusIcon status={status} small displayTooltip={false} />
          <span
            className={styles.runTaskName}
          >
            {highlightText(name, searchText)}
          </span>
        </div>
        {infos}
      </div>
    );
  }
}

RunTask.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  task: PropTypes.shape({
    name: PropTypes.string,
    status: PropTypes.string
  }),
  timings: PropTypes.bool,
  searchText: PropTypes.string,
  active: PropTypes.bool,
  onTaskClick: PropTypes.func
};

export default RunTask;
