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
  nextflowTaskStatusCreated,
  nextflowTaskStatusSubmitted,
  nextflowTaskStatusRunning,
  nextflowTaskStatusCompleted,
  nextflowTaskStatusFailed,
  nextflowTaskStatusAborted,
  nextflowTaskStatusCached
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
      return 'cp-grey-light';
    case nextflowTaskStatusSubmitted:
      return 'cp-aqua-accent';
    case nextflowTaskStatusCached:
      return 'cp-violet';
    default:
      return 'cp-text-not-important';
  }
}

export function getBarClassNameForNextflowTaskStatus (status) {
  switch ((status || '').toUpperCase()) {
    case nextflowTaskStatusSubmitted:
      return 'cp-aqua-light';
    default:
      return getClassNameForNextflowTaskStatus(status);
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
      return 'grey-light';
    case nextflowTaskStatusSubmitted:
      return 'aqua-accent';
    case nextflowTaskStatusCached:
      return 'violet';
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

export function getSortedTaskStatuses (statuses, includeAllStatuses = true) {
  return nextflowTaskStatusGroups.map((st) => ({
    status: st.statuses[0],
    count: statuses.filter((s) => st.statuses.includes(s.status)).reduce((r, c) => r + c.count, 0)
  })).filter((o) => includeAllStatuses || o.count > 0);
}

function isStatusFilled (status) {
  switch ((status || '').toUpperCase()) {
    case nextflowTaskStatusCompleted:
    case nextflowTaskStatusAborted:
    case nextflowTaskStatusFailed:
    case nextflowTaskStatusRunning:
      return true;
    default:
      return false;
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
  const asTag = Boolean(filled && tagName && showLabel);
  const tagFilled = asTag && isStatusFilled(status);
  return (
    <span
      className={classNames(
        className,
        styles.nfTaskStatusIcon,
        {
          'cp-tag': asTag,
          filled: tagFilled,
          [getTagNameForNextflowTaskStatus(status)]: asTag
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
