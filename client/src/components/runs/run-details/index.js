import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {inject, observer} from 'mobx-react';
import {Alert} from 'antd';
import RunHeader from './widgets/run-header';
import fetchRunInfo from '../logs/misc/fetch-run-info';
import {MAX_NESTED_RUNS_TO_DISPLAY} from './constants';
import {getRunTabs} from './widgets/run-tabs/tabs';
import RunTabs from './widgets/run-tabs';
import {runPipelineActions} from '../actions';
import {fetchRunCliCommands, fetchRunPayload} from './utilities/loaders';
import styles from './run-details.css';

@inject((_, props) => {
  const {params = {}, runId: propRunId, preLoadedRun} = props || {};
  const {
    runId = propRunId
  } = params;
  return {
    runId: Number.isNaN(Number(runId)) ? undefined : Number(runId),
    preLoadedRun
  };
})
@inject(
  'preferences',
  'dtsList',
  'multiZoneManager',
  'dockerRegistries',
  'preferences',
  'uiNavigation'
)
@runPipelineActions
@observer
class RunDetails extends React.Component {
  state = {
    run: undefined,
    pending: false,
    loaded: false,
    error: undefined,
    nestedRuns: [],
    hasNestedRuns: false,
    totalNestedRuns: 0,
    nestedRunsPending: false,
    runTasks: [],
    runTasksLoaded: false,
    tab: undefined,
    tabs: [],
    runPayload: undefined,
    cliCommands: undefined,
    cliCommandsPending: false,
    cliCommandsError: undefined
  };

  componentDidMount () {
    this.onRunChanged();
  }
  componentDidUpdate (prevProps) {
    if (prevProps.runId !== this.props.runId) {
      this.onRunChanged();
    }
  }
  componentWillUnmount () {
    this.stopAutoUpdate();
    this.cliCommandsToken = undefined;
  }
  stopAutoUpdate = () => {
    this.token = {};
    if (typeof this.stop === 'function') {
      this.stop();
    }
    this.stop = undefined;
    this.reFetchRunInfo = undefined;
  };
  getActiveTab = () => {
    const {tab: currentTabName, tabs = []} = this.state;
    const defaultTab = tabs[0];
    const aTab = tabs.find((t) => t.tab === currentTabName);
    return aTab || defaultTab;
  };
  onRunChanged = () => {
    this.stopAutoUpdate();
    const token = this.token = {};
    const {
      runId,
      preLoadedRun,
      preferences,
      dockerRegistries,
      uiNavigation
    } = this.props;
    this.fetchRunCliCommands(undefined);
    if (runId) {
      this.setState({
        run: preLoadedRun && preLoadedRun.id === runId ? preLoadedRun : preLoadedRun,
        pending: true,
        loaded: preLoadedRun && preLoadedRun.id === runId,
        error: undefined,
        nestedRuns: [],
        hasNestedRuns: false,
        totalNestedRuns: 0,
        nestedRunsPending: false,
        runTasks: [],
        runTasksLoaded: false,
        tab: undefined,
        tabs: preLoadedRun && preLoadedRun.id === runId ? getRunTabs(preLoadedRun) : []
      }, async () => {
        let fetchCliCommands = true;
        if (preLoadedRun && preLoadedRun.id === runId) {
          this.fetchRunCliCommands(preLoadedRun);
          fetchCliCommands = false;
        }
        const commit = (data = {}) => {
          if (token === this.token) {
            this.setState({
              pending: false,
              loaded: true,
              runTasksLoaded: true,
              ...data,
              tabs: data.run ? getRunTabs(data.run) : []
            });
          }
        };
        try {
          await Promise.all([
            uiNavigation.fetch(),
            preferences.fetchIfNeededOrWait()
          ]);
          const {
            stop,
            fetch: reFetch,
            data
          } = await fetchRunInfo(runId, commit, {
            preferences,
            dockerRegistries,
            maxNestedRunsToDisplay: MAX_NESTED_RUNS_TO_DISPLAY
          });
          if (fetchCliCommands) {
            this.fetchRunCliCommands(data);
          }
          this.stop = stop;
          this.reFetchRunInfo = reFetch;
        } catch (error) {
          commit({error: error.message});
        }
      });
    } else {
      this.setState({
        run: undefined,
        pending: false,
        loaded: false,
        error: undefined,
        nestedRuns: [],
        hasNestedRuns: false,
        totalNestedRuns: 0,
        nestedRunsPending: false,
        runTasks: [],
        runTasksLoaded: false,
        tab: undefined,
        tabs: []
      });
    }
  };

  fetchRunCliCommands = (run) => {
    const cliCommandsToken = this.cliCommandsToken = {};
    if (run) {
      this.setState({
        runPayload: undefined,
        cliCommands: undefined,
        cliCommandsPending: true,
        cliCommandsError: undefined
      }, async () => {
        const commit = (fn) => {
          if (cliCommandsToken === this.cliCommandsToken) {
            fn();
          }
        };
        try {
          const payload = await fetchRunPayload(run);
          const commands = await fetchRunCliCommands(run, payload);
          commit(() => {
            this.setState({
              cliCommands: commands,
              runPayload: payload,
              cliCommandsPending: false,
              cliCommandsError: undefined
            });
          });
        } catch (error) {
          commit(() => {
            this.setState({
              cliCommands: undefined,
              runPayload: undefined,
              cliCommandsPending: false,
              cliCommandsError: error.message
            });
          });
        }
      });
    } else {
      this.setState({
        cliCommands: undefined,
        runPayload: undefined,
        cliCommandsPending: false,
        cliCommandsError: undefined
      });
    }
  }

  onTabChanged = (newTab) => this.setState({tab: newTab});

  renderContent = (currentTab) => {
    const {error, pending} = this.state;
    if (error && !pending) {
      return (
        <Alert message={error} showIcon type="error" />
      );
    }
    if (currentTab && currentTab.render && typeof currentTab.render === 'function') {
      if (currentTab.asPanel === false) {
        return currentTab.render(this.state);
      }
      return (
        <div
          className={classNames(
            styles.runDetailsContent,
            styles.runDetailsSection,
            'cp-panel',
            'cp-panel-no-hover',
            'cp-panel-borderless'
          )}
        >
          {currentTab.render(this.state)}
        </div>
      );
    }
    return null;
  };

  onRefreshRunInfo = () => {
    if (this.reFetchRunInfo) {
      this.reFetchRunInfo();
    } else {
      this.onRunChanged();
    }
  };

  render () {
    const {
      className,
      style,
      runId
    } = this.props;
    const {
      run,
      loaded,
      runTasks,
      runTasksLoaded,
      tabs
    } = this.state;
    const tab = this.getActiveTab();
    return (
      <div
        className={classNames(
          className,
          styles.runDetails
        )}
        style={style}
      >
        <RunHeader
          className={classNames(
            styles.runHeader,
            styles.runDetailsSection,
            'cp-panel',
            'cp-panel-no-hover',
            'cp-panel-borderless'
          )}
          run={run}
          runId={runId}
          runTasks={runTasks}
          loaded={loaded}
          runTasksLoaded={runTasksLoaded}
          onRefreshRunInfo={this.onRefreshRunInfo}
        />
        <RunTabs
          className={classNames(
            styles.runDetailsTabs,
            styles.runDetailsSection,
            'cp-panel',
            'cp-panel-no-hover',
            'cp-panel-borderless'
          )}
          run={run}
          tab={tab ? tab.tab : undefined}
          tabs={tabs}
          onTabChange={this.onTabChanged}
        />
        {this.renderContent(tab)}
      </div>
    );
  }
}

RunDetails.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object
};

export default RunDetails;
