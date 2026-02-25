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
import {
  observer} from 'mobx-react';
import moment from 'moment-timezone';
import {
  Popover
} from 'antd';
import { InfoCircleOutlined } from '@ant-design/icons';
import classNames from 'classnames';
import getRunDurationInfo, {
  getIntervalDuration,
  RunHistoryPhase
} from '../../../../utils/run-duration';
import displayDate from '../../../../utils/displayDate';
import {displayDurationInSeconds} from '../../../../utils/displayDuration';
import styles from './run-timeline-info.css';

const defaultDateFormat = 'D MMMM, YYYY, HH:mm';

function RunTimelineInfoDetails (props) {
  const {
    className,
    style,
    run,
    runTasks,
    analyseSchedulingPhase,
    dateFormat = defaultDateFormat
  } = props;
  if (!run) {
    return null;
  }
  const {
    pausedIntervals
  } = getRunDurationInfo(
    run,
    analyseSchedulingPhase,
    runTasks || []
  );
  if (pausedIntervals.length > 0) {
    return (
      <div className={classNames(styles.runTimelineInfoDetails, className)} style={style}>
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
                    <td>{displayDate(start, dateFormat)}</td>
                    <td>{displayDate(end, dateFormat)}</td>
                    <td>{displayDurationInSeconds(getIntervalDuration(interval), true)}</td>
                  </tr>
                );
              })
            }
          </tbody>
        </table>
      </div>
    );
  }
  return null;
}

RunTimelineInfoDetails.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object,
  runTasks: PropTypes.oneOfType([PropTypes.object, PropTypes.array]),
  analyseSchedulingPhase: PropTypes.bool,
  dateFormat: PropTypes.string
};

RunTimelineInfoDetails.available = function (
  run,
  runTasks,
  analyseSchedulingPhase
) {
  const {
    pausedIntervals,
    runningDate
  } = getRunDurationInfo(
    run,
    analyseSchedulingPhase,
    runTasks || []
  );
  return runningDate && pausedIntervals.length > 0;
};

function getRunTimelineInfoDescription (
  run,
  runTasks,
  analyseSchedulingPhase,
  dateFormat
) {
  if (!run) {
    return null;
  }
  const {
    status
  } = run;
  const {
    info,
    last,
    runningDate,
    pausedDuration,
    totalRunningDuration,
    wasPaused
  } = getRunDurationInfo(
    run,
    analyseSchedulingPhase,
    runTasks || []
  );
  if (!runningDate) {
    return null;
  }
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
  return isStopped
    ? `${displayDate(info.end || moment.utc(), dateFormat)} (${durationInfo})`
    : durationInfo;
}

function RunTimelineInfo (
  {
    className,
    style,
    run,
    runTasks,
    analyseSchedulingPhase,
    showDetails = true,
    dateFormat
  }
) {
  if (!run) {
    return null;
  }
  const infoString = getRunTimelineInfoDescription(
    run,
    runTasks,
    analyseSchedulingPhase,
    dateFormat
  );
  if (infoString) {
    const details = showDetails
      ? RunTimelineInfoDetails.available(run, runTasks, analyseSchedulingPhase)
      : false;
    if (details) {
      return (
        <Popover
          content={(
            <RunTimelineInfoDetails
              run={run}
              runTasks={runTasks}
              analyseSchedulingPhase={analyseSchedulingPhase}
              dateFormat={dateFormat}
            />
          )}
        >
          <span className={className} style={{...(style || {}), cursor: 'pointer'}}>
            {infoString}
            <InfoCircleOutlined style={{marginLeft: 5}} />
          </span>
        </Popover>
      );
    }
    return (
      <span className={className} style={style}>
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
  analyseSchedulingPhase: PropTypes.bool,
  showDetails: PropTypes.bool,
  dateFormat: PropTypes.string
};

RunTimelineInfo.defaultProps = {
  showDetails: true
};

RunTimelineInfo.Details = observer(RunTimelineInfoDetails);

export default observer(RunTimelineInfo);
export {RunTimelineInfoDetails};
