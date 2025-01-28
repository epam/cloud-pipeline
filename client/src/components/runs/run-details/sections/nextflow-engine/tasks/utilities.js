import React from 'react';
import PropTypes from 'prop-types';
import {Icon} from 'antd';
import classNames from 'classnames';
import styles from './nextflow-engine-tasks.css';

export const nextflowTaskStatusCompleted = 'COMPLETED';
export const nextflowTaskStatusFailed = 'FAILED';
export const nextflowTaskStatusAborted = 'ABORTED';
export const nextflowTaskStatusRunning = 'RUNNING';
export const nextflowTaskStatusCreated = 'CREATED';
export const nextflowTaskStatusSubmitted = 'SUBMITTED';
export const nextflowTaskStatusCached = 'CACHED';

export const nextflowTaskStatusGroupCompleted = {
  group: 'COMPLETED',
  statuses: [nextflowTaskStatusCompleted]
};
export const nextflowTaskStatusGroupFailed = {
  group: 'FAILED',
  statuses: [nextflowTaskStatusFailed]
};
export const nextflowTaskStatusGroupAborted = {
  group: 'ABORTED',
  statuses: [nextflowTaskStatusAborted]
};
export const nextflowTaskStatusGroupRunning = {
  group: 'RUNNING',
  statuses: [nextflowTaskStatusRunning]
};
export const nextflowTaskStatusGroupCreated = {
  group: 'CREATED',
  statuses: [nextflowTaskStatusCreated]
};
export const nextflowTaskStatusGroupSubmitted = {
  group: 'SUBMITTED',
  statuses: [nextflowTaskStatusSubmitted]
};
export const nextflowTaskStatusGroupCached = {
  group: 'CACHED',
  statuses: [nextflowTaskStatusCached]
};

export const nextflowTaskStatuses = [
  nextflowTaskStatusCompleted,
  nextflowTaskStatusFailed,
  nextflowTaskStatusAborted,
  nextflowTaskStatusRunning,
  nextflowTaskStatusCreated,
  nextflowTaskStatusSubmitted,
  nextflowTaskStatusGroupCached
];

export const nextflowTaskStatusGroups = [
  nextflowTaskStatusGroupCreated,
  nextflowTaskStatusGroupSubmitted,
  nextflowTaskStatusGroupRunning,
  nextflowTaskStatusGroupCompleted,
  nextflowTaskStatusGroupFailed,
  nextflowTaskStatusGroupAborted,
  nextflowTaskStatusGroupCached
];

export function getClassNameForNextflowTaskStatus (status) {
  switch ((status || '').toUpperCase()) {
    case nextflowTaskStatusCompleted:
      return 'cp-success';
    case nextflowTaskStatusAborted:
      return 'cp-warning';
    case nextflowTaskStatusFailed:
      return 'cp-error';
    case nextflowTaskStatusRunning:
      return 'cp-primary';
    case nextflowTaskStatusCreated:
    default:
      return 'cp-text-not-important';
  }
}

export function getTagNameForNextflowTaskStatus (status) {
  switch ((status || '').toUpperCase()) {
    case nextflowTaskStatusCompleted:
      return 'success';
    case nextflowTaskStatusAborted:
      return 'warning';
    case nextflowTaskStatusFailed:
      return 'critical';
    case nextflowTaskStatusRunning:
      return 'primary';
    case nextflowTaskStatusCreated:
    default:
      return undefined;
  }
}

export function getIconForStatus (status, filled = false) {
  switch ((status || '').toUpperCase()) {
    case nextflowTaskStatusCompleted:
      return filled ? 'check-circle' : 'check-circle-o';
    case nextflowTaskStatusAborted:
      return filled ? 'close-circle' : 'close-circle-o';
    case nextflowTaskStatusFailed:
      return filled ? 'exclamation-circle' : 'exclamation-circle-o';
    case nextflowTaskStatusRunning:
      return filled ? 'play-circle' : 'play-circle-o';
    case nextflowTaskStatusSubmitted:
      return filled ? 'plus-circle' : 'plus-circle-o';
    case nextflowTaskStatusCached:
      return filled ? 'minus-circle' : 'minus-circle-o';
    case nextflowTaskStatusCreated:
    default:
      return filled ? 'clock-circle' : 'clock-circle-o';
  }
}

function NextflowTaskStatus (props) {
  const {
    className,
    style,
    status,
    filled = true,
    showLabel = true
  } = props;
  const tagName = getTagNameForNextflowTaskStatus(status);
  const tagFilled = Boolean(filled && tagName && showLabel);
  return (
    <span
      className={classNames(
        className,
        styles.nfTaskStatusIcon,
        {
          'cp-tag': tagFilled,
          filled: tagFilled,
          [getTagNameForNextflowTaskStatus(status)]: tagFilled
        }
      )}
      style={style}
    >
      <Icon
        type={getIconForStatus(status, tagFilled)}
        className={tagFilled ? undefined : getClassNameForNextflowTaskStatus(status)}
      />
      {
        showLabel && (
          <span style={{marginLeft: 3}}>
            {status.toUpperCase()}
          </span>
        )
      }
    </span>
  );
}

NextflowTaskStatus.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  status: PropTypes.string,
  filled: PropTypes.bool,
  showLabel: PropTypes.bool
};

export {NextflowTaskStatus};
