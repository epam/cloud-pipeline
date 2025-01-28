import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Icon, Popover} from 'antd';
import getRunDurationInfo from '../../../../../utils/run-duration';
import displayDate from '../../../../../utils/displayDate';
import {displayDurationInSeconds} from '../../../../../utils/displayDuration';
import RunTimelineInfo from '../../../logs/misc/run-timeline-info';
import styles from './run-timeline.css';

function renderInfo (info, asTable = false) {
  if (!info) {
    return null;
  }
  const {
    key,
    title,
    value
  } = info;
  if (asTable) {
    return (
      <tr key={key}>
        <td>{title}:</td>
        <td>{value}</td>
      </tr>
    );
  }
  return (
    <span key={key}>
      <span style={{marginRight: 5}}>{title}:</span>
      {typeof value === 'string' ? <span>{value}</span> : value}
    </span>
  );
}

function RunTimeline (props) {
  const {
    className,
    style,
    run,
    runTasks = [],
    showIcon
  } = props;
  if (!run) {
    return null;
  }
  const {
    status
  } = run;
  const info = getRunDurationInfo(
    run,
    true,
    runTasks
  );
  const {
    scheduledDate,
    runningDate,
    schedulingDuration,
    totalDuration
  } = info;
  const scheduled = {
    key: 'scheduled',
    title: 'Scheduled',
    value: displayDate(scheduledDate)
  };
  const started = (() => {
    if (runningDate && runTasks.length > 0) {
      return {
        key: 'started',
        title: 'Started',
        value: (
          <span>
            <span>{displayDate(runningDate)}</span>
            <span style={{marginLeft: 5}}>({displayDurationInSeconds(schedulingDuration)})</span>
          </span>
        )
      };
    }
    return undefined;
  })();
  const waiting = (() => {
    if (!runningDate || runTasks.length === 0) {
      return {
        key: 'waiting',
        title: 'Waiting for',
        value: displayDurationInSeconds(totalDuration)
      };
    }
    return undefined;
  })();
  const finished = (() => {
    if (runningDate && runTasks.length > 0) {
      const statusLabel = (() => {
        switch ((status || '').toUpperCase()) {
          case 'SUCCESS':
          case 'FAILURE':
            return 'Finished';
          case 'STOPPED':
            return 'Stopped at';
          default:
            return 'Running for';
        }
      })();
      return {
        key: 'finished',
        title: statusLabel,
        value: (
          <RunTimelineInfo
            run={run}
            runTasks={runTasks}
            analyseSchedulingPhase
          />
        )
      };
    }
    return undefined;
  })();
  const first = started || scheduled;
  const last = finished || waiting;
  const details = (
    <table className={styles.runTimelineTable}>
      <tbody>
        {renderInfo(scheduled, true)}
        {renderInfo(waiting, true)}
        {renderInfo(started, true)}
        {renderInfo(finished, true)}
      </tbody>
    </table>
  );
  return (
    <Popover content={details}>
      <div
        className={classNames(className, styles.runTimeline)}
        style={style}
      >
        {showIcon && (
          <Icon type="clock-circle-o" style={{marginRight: 5, fontSize: '0.75rem'}} />
        )}
        <div className={styles.runTimelineInfo}>
          {renderInfo(first)}
          {renderInfo(last)}
        </div>
      </div>
    </Popover>
  );
}

RunTimeline.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object,
  runTasks: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  showIcon: PropTypes.bool
};

export default RunTimeline;
