import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './run-header.css';
import StatusIcon from '../../../../special/run-status-icon';
import RunName from '../../../run-name';
import RunOwner from './run-owner';
import RunExecutable from '../run-executable';
import RunTimeline from '../run-timeline';
import RunNetworkLimit from '../run-network-limit';
import RunFailureReason from '../run-failure-reason';
import RunStateReason from '../run-state-reason';
import RunEstimatedPrice from '../run-estimated-price';
import RunActions from './run-actions';

class RunHeader extends React.PureComponent {
  render () {
    const {
      className,
      style,
      run,
      runId,
      runTasks,
      loaded,
      onRefreshRunInfo
    } = this.props;
    const {
      id = runId
    } = run || {};
    return (
      <div
        className={classNames(className, styles.runHeader)}
        style={style}
      >
        <div className={classNames(styles.runTitle, styles.runHeaderRow)}>
          <div className={styles.runName}>
            {loaded && <StatusIcon run={run} />}
            <span>Run</span>
            {
              id && (
                <RunName.AutoUpdate
                  run={run}
                  editable
                  onRefresh={this.refreshRunInfo}
                  ignoreOffset
                >
                  #{id}
                </RunName.AutoUpdate>
              )
            }
          </div>
          <RunOwner run={run} className={styles.runOwner} />
          <RunExecutable run={run} className={styles.runExecutable} />
          <div style={{marginLeft: 'auto'}}>
            <RunActions run={run} onRefreshRunInfo={onRefreshRunInfo} />
          </div>
        </div>
        <RunFailureReason run={run} className={styles.runHeaderDetails} />
        <RunStateReason run={run} className={styles.runHeaderDetails} />
        <RunNetworkLimit run={run} className={styles.runHeaderDetails} />
        {loaded && (
          <div className={classNames(styles.runHeaderRow, styles.runHeaderDetails)}>
            <RunTimeline run={run} runTasks={runTasks} />
          </div>
        )}
        {loaded && (
          <div
            className={classNames(
              styles.runHeaderRow,
              styles.runHeaderDetails
            )}
          >
            <RunEstimatedPrice run={run} runTasks={runTasks} />
          </div>
        )}
      </div>
    );
  }
}

RunHeader.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object,
  runTasks: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  runId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  loaded: PropTypes.bool,
  onRefreshRunInfo: PropTypes.func
};

RunHeader.defaultProps = {
  loaded: true
};

export default RunHeader;
