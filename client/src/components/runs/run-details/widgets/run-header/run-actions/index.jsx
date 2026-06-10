import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './run-actions.module.css';
import RunActionButton from './run-action-button';
import RunPauseResumeButton from './run-pause-resume-button';
import RunContinueButton from './run-continue-button';

function RunActions(props) {
  const {className, style, run, onRefreshRunInfo} = props;
  if (!run) {
    return null;
  }
  return (
    <div
      className={classNames(className, styles.runActions)}
      style={{...(style || {}), display: 'inline-flex', alignItems: 'center'}}
    >
      <RunPauseResumeButton run={run} onRefreshRunInfo={onRefreshRunInfo} />
      <RunActionButton run={run} onRefreshRunInfo={onRefreshRunInfo} />
      <RunContinueButton run={run} onRefreshRunInfo={onRefreshRunInfo} />
    </div>
  );
}

RunActions.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object,
  onRefreshRunInfo: PropTypes.func,
};

export default RunActions;
