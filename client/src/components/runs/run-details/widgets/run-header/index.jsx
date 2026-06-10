import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {inject, observer} from 'mobx-react';
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
import RunTags, {KNOWN_TAG_NAMES} from '../../../run-tags';
import styles from './run-header.module.css';
import RunEndpoints from './run-endpoints';
import LogsModeButton from '../../../logs/logs-mode';

function RunHeader(props) {
  const {
    className,
    style,
    run,
    runId,
    runTasks,
    loaded = true,
    runTasksLoaded,
    onRefreshRunInfo,
    preferences,
    currentMode,
    onChangeMode,
    modes,
    showEstimatedPrice = true,
  } = props;
  const {id = runId} = run || {};
  return (
    <div className={classNames(className, styles.runHeader)} style={style}>
      <div className={classNames(styles.runTitle, styles.runHeaderRow)}>
        <div className={styles.runName}>
          {loaded && <StatusIcon run={run} />}
          <span>Run</span>
          {id && (
            <RunName.AutoUpdate run={run} editable onRefresh={onRefreshRunInfo} ignoreOffset>
              #{id}
            </RunName.AutoUpdate>
          )}
        </div>
        <RunOwner run={run} className={styles.runOwner} />
        <RunExecutable run={run} className={styles.runExecutable} />
        <LogsModeButton
          style={{marginLeft: 5}}
          current={currentMode}
          modes={modes}
          onChangeMode={onChangeMode}
        />
        <div style={{marginLeft: 'auto'}}>
          <RunActions run={run} onRefreshRunInfo={onRefreshRunInfo} />
        </div>
      </div>
      <RunEndpoints
        run={run}
        className={classNames(styles.runHeaderDetails, styles.runEndpoints)}
      />
      <RunFailureReason run={run} className={styles.runHeaderDetails} />
      <RunStateReason run={run} className={styles.runHeaderDetails} />
      <RunNetworkLimit run={run} className={styles.runHeaderDetails} />
      {loaded && (
        <div className={classNames(styles.runHeaderRow, styles.runHeaderDetails)}>
          <RunTimeline run={run} runTasks={runTasks} loaded={loaded && runTasksLoaded} />
        </div>
      )}
      {loaded && showEstimatedPrice ? (
        <div className={classNames(styles.runHeaderRow, styles.runHeaderDetails)}>
          <RunEstimatedPrice run={run} runTasks={runTasks} />
        </div>
      ) : null}
      {loaded && RunTags.shouldDisplayTags(run, preferences) && (
        <RunTags
          className={classNames(styles.runHeaderRow, styles.runHeaderDetails)}
          run={run}
          excludeTags={[KNOWN_TAG_NAMES.network_limit]}
        />
      )}
    </div>
  );
}

RunHeader.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object,
  runTasks: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  runId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  loaded: PropTypes.bool,
  runTasksLoaded: PropTypes.bool,
  onRefreshRunInfo: PropTypes.func,
  currentMode: PropTypes.string,
  modes: PropTypes.arrayOf(PropTypes.string),
  onChangeMode: PropTypes.func,
  showEstimatedPrice: PropTypes.bool,
};

export default inject('preferences')(observer(RunHeader));
