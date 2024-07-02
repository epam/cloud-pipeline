/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Alert} from 'antd';
import {Link} from 'react-router';
import classNames from 'classnames';
import PipelineRunInfo from '../../../../../models/pipelines/PipelineRunInfo';
import AWSRegionTag from '../../../../special/AWSRegionTag';
import styles from './restarted-runs-info.css';

function RunLink ({runId, regionId}) {
  return (
    <span className={styles.restartedRun}>
      <Link to={`/run/${runId}`}>
        <span
          className={
            classNames(
              styles.restartedRunLink,
              'cp-run-nested-run-link'
            )
          }
        >
          <b>#{runId}</b>
          {
            regionId && (
              <AWSRegionTag
                regionId={regionId}
                style={{marginLeft: 5}}
                flagStyle={{fontSize: 'larger'}}
                providerStyle={{fontSize: 'larger'}}
                showProvider
                displayName
              />
            )
          }
        </span>
      </Link>
    </span>
  );
}

class RestartedRunsInfo extends React.Component {
  state = {
    currentRun: undefined,
    restartedRuns: [],
    initialRun: undefined,
    pending: false
  };

  fetchToken = 0;

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.run !== this.props.run ||
      prevProps.runId !== this.props.runId
    ) {
      this.updateFromProps();
    }
  }

  updateFromProps = () => {
    const {
      run: runProps,
      runId
    } = this.props;
    this.fetchToken += 1;
    const token = this.fetchToken;
    const parseRunInfo = async (run) => {
      const {
        id: currentRunId,
        restartedRuns = []
      } = run;
      if (restartedRuns.length === 0) {
        this.setState({
          currentRun: undefined,
          restartedRuns: [],
          initialRun: undefined,
          pending: false
        });
        return;
      }
      const parentRun = restartedRuns
        .find((aRun) => aRun.restartedRunId === currentRunId);
      const childRuns = restartedRuns
        .filter((aRun) => aRun.parentRunId === currentRunId)
        .map((aRun) => ({
          id: aRun.restartedRunId,
          regionId: aRun.restartedRunRegionId
        }));
      this.setState({
        currentRun: run,
        restartedRuns: childRuns,
        initialRun: parentRun
          ? {id: parentRun.parentRunId, regionId: parentRun.parentRunRegionId}
          : undefined,
        pending: false
      });
    };
    if (runProps) {
      (parseRunInfo)(runProps);
    } else if (runId) {
      this.setState({
        currentRun: undefined,
        restartedRuns: [],
        initialRun: undefined,
        pending: true
      }, async () => {
        try {
          const info = new PipelineRunInfo(runId);
          await info.fetch();
          if (info.error || !info.loaded) {
            throw new Error(info.error || `Error loading run #${runId} info`);
          }
          await parseRunInfo(info.value);
        } catch (error) {
          console.warn(error.message);
        } finally {
          if (token === this.fetchToken) {
            this.setState({pending: false});
          }
        }
      });
    } else {
      this.setState({
        currentRun: undefined,
        restartedRuns: [],
        initialRun: undefined,
        pending: false
      });
    }
  };

  render () {
    const {
      className,
      style
    } = this.props;
    const {
      currentRun,
      restartedRuns = [],
      initialRun
    } = this.state;
    if (!currentRun) {
      return null;
    }
    const alerts = [];
    if (initialRun) {
      alerts.push((
        <Alert
          key="initial-run-alert"
          className={styles.restartedRunInfoAlert}
          type="info"
          message={(
            <div className={styles.restartedRunInfoAlertContent}>
              This run was launched instead of
              <RunLink
                runId={initialRun.id}
                regionId={initialRun.regionId}
              />
            </div>
          )}
        />
      ));
    }
    if (restartedRuns.length > 0) {
      alerts.push((
        <Alert
          key="restarted-runs-alert"
          type="info"
          className={styles.restartedRunInfoAlert}
          message={(
            <div className={styles.restartedRunInfoAlertContent}>
              The following run{restartedRuns.length > 1 ? 's' : ''}
              {restartedRuns.length > 1 ? ' were launched' : ' was launched'}
              {' instead of current one:'}
              {
                restartedRuns.map((run) => (
                  <RunLink
                    key={run.id}
                    runId={run.id}
                    regionId={run.regionId}
                  />
                ))
              }
            </div>
          )}
        />
      ));
    }
    if (alerts.length > 0) {
      return (
        <div
          className={className}
          style={style}
        >
          {alerts}
        </div>
      );
    }
    return null;
  }
}

RestartedRunsInfo.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  runId: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  run: PropTypes.object
};

export default RestartedRunsInfo;
