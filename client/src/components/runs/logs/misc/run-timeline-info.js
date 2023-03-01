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
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import moment from 'moment-timezone';
import {Icon, Popover} from 'antd';
import classNames from 'classnames';
import getRunDurationInfo, {
  getIntervalDuration,
  RunHistoryPhase
} from '../../../../utils/run-duration';
import displayDate from '../../../../utils/displayDate';
import {displayDurationInSeconds} from '../../../../utils/displayDuration';
import styles from './run-timeline-info.css';

function RunTimelineInfo (
  {
    className,
    style,
    run,
    runTasks,
    analyseSchedulingPhase
  }
) {
  if (!run) {
    return null;
  }
  const {
    info,
    last,
    runningDate,
    pausedDuration,
    totalRunningDuration,
    wasPaused,
    pausedIntervals
  } = getRunDurationInfo(
    run,
    analyseSchedulingPhase,
    runTasks || []
  );
  const {
    status
  } = run;
  if (runningDate && (!analyseSchedulingPhase || (runTasks || []).length > 0)) {
    const isStopped = ['SUCCESS', 'FAILURE', 'STOPPED'].includes((status || '').toUpperCase());
    const durationInfos = [displayDurationInSeconds(totalRunningDuration)];
    if (pausedDuration > 0) {
      durationInfos.push(`${displayDurationInSeconds(pausedDuration)} in pause`);
    }
    if (!isStopped && wasPaused && last && last.phase === RunHistoryPhase.running) {
      durationInfos.push(
        `${displayDurationInSeconds(getIntervalDuration(last))} since last resume`
      );
    }
    const durationInfo = durationInfos.join(' / ');
    const infoString = isStopped
      ? `${displayDate(info.end || moment.utc())} (${durationInfo})`
      : durationInfo;
    if (pausedIntervals.length > 0) {
      return (
        <Popover
          content={(
            <div className={styles.runTimelineInfoDetails}>
              <table
                className={
                  classNames(
                    'cp-run-timeline-table',
                    styles.pausedDurationTable
                  )
                }
              >
                <thead>
                  <tr>
                    <th>Paused</th>
                    <th>Resumed / Stopped</th>
                    <th>Duration</th>
                  </tr>
                </thead>
                <tbody>
                  {
                    pausedIntervals.map((interval, index) => {
                      const {
                        start,
                        end
                      } = interval;
                      return (
                        <tr key={`interval-${index}`}>
                          <td>{displayDate(start, 'D MMMM, YYYY, HH:mm')}</td>
                          <td>{displayDate(end, 'D MMMM, YYYY, HH:mm')}</td>
                          <td>{displayDurationInSeconds(getIntervalDuration(interval), true)}</td>
                        </tr>
                      );
                    })
                  }
                </tbody>
              </table>
            </div>
          )}
        >
          <span style={{cursor: 'pointer'}}>
            {infoString}
            <Icon type="info-circle-o" style={{marginLeft: 5}} />
          </span>
        </Popover>
      );
    }
    return (
      <span>
        {infoString}
      </span>
    );
  }
  return null;
}

RunTimelineInfo.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object,
  runTasks: PropTypes.oneOfType([PropTypes.object, PropTypes.array]),
  analyseSchedulingPhase: PropTypes.bool
};

export default observer(RunTimelineInfo);
