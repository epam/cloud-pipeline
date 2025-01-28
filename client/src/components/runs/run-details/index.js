import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {inject, observer} from 'mobx-react';
import styles from './run-details.css';
import RunHeader from './widgets/run-header';
import RunSchedules from '../../../models/runSchedule/RunSchedules';
import fetchRunInfo from '../logs/misc/fetch-run-info';
import {MAX_NESTED_RUNS_TO_DISPLAY} from './constants';
import {getRunTabs} from './widgets/run-tabs/tabs';
import RunTabs from './widgets/run-tabs';
import {runPipelineActions} from '../actions';

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
    task: undefined,
    run: undefined,
    pending: false,
    loaded: false,
    error: undefined,
    nestedRuns: [],
    hasNestedRuns: false,
    totalNestedRuns: 0,
    nestedRunsPending: false,
    showActiveWorkersOnly: false,
    runTasks: [],
    language: undefined,
    tasksCollapsed: false,
    runDataLoaded: false,
    runPreviousStatus: undefined,
    tab: undefined
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
  }
  stopAutoUpdate = () => {
    this.token = {};
    if (typeof this.stop === 'function') {
      this.stop();
    }
    this.stop = undefined;
    this.reFetchRunInfo = undefined;
  };
  /**
   * Checks if the form should be navigated to a specific task
   */
  checkTaskNavigation = () => {
    const {
      uiNavigation
    } = this.props;
    // `task` holds current selected run task
    let {
      task
    } = this.state;
    const {
      runTasks = [],
      run,
      tasksCollapsed: currentTaskCollapsed,
      runPreviousStatus
    } = this.state;
    let taskToNavigate;
    if (!task && runTasks.length > 0) {
      // If no task is selected and there are some tasks in run -
      // we need to navigate to any of it
      taskToNavigate = runTasks[0];
    }
    const {
      pipelineName,
      podId,
      status
    } = run;
    let tasksCollapsed = currentTaskCollapsed;
    const runningStatuses = ['RUNNING', 'PAUSED', 'PAUSING', 'RESUMING'];
    if (runPreviousStatus !== status && !runningStatuses.includes(status)) {
      // Run status changed to "FAILURE", "STOPPED" or "SUCCESS".
      // We should switch to the "main" task in that case
      task = undefined; // that will force task navigation
    }
    if (uiNavigation.runLogsMainTask && !task) {
      // user has "ui-run-logs-main-task" set to true ("display main task by default").
      // we need to navigate to "pipeline" task (if it is finished) or "console" task,
      // if there isn't selected task
      const pipelineTaskName = pipelineName || podId || '';
      const pipelineTask = runTasks
        .find((t) => (t.name || '').toLowerCase() === pipelineTaskName.toLowerCase());
      const consoleTask = runTasks
        .find((t) => (t.name || '').toLowerCase() === 'console');
      if (
        pipelineTask &&
        pipelineTask.status &&
        !runningStatuses.includes(pipelineTask.status.toUpperCase())
      ) {
        // run has finished "pipeline" task - we should navigate to it
        taskToNavigate = pipelineTask;
        tasksCollapsed = true;
      } else if (consoleTask) {
        // run doesn't have finished "pipeline" task - we should navigate to console task
        taskToNavigate = consoleTask;
        tasksCollapsed = true;
      }
    }
    if (runPreviousStatus !== status || currentTaskCollapsed !== tasksCollapsed) {
      this.setState({
        tasksCollapsed,
        runPreviousStatus: status
      });
    }
    if (
      taskToNavigate &&
      (!task || task.name.toLowerCase() !== taskToNavigate.name.toLowerCase())
    ) {
      // there is a task to be navigated to (`taskToNavigate`)
      // and current selected task either is missing or differs from the `taskToNavigate`
      this.setState({
        task: taskToNavigate
      });
    }
  };
  getActiveTab = () => {
    const {run, tab: currentTabName} = this.state;
    const tabs = getRunTabs(run);
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
        showActiveWorkersOnly: false,
        runTasks: [],
        language: undefined,
        tasksCollapsed: false,
        runDataLoaded: false,
        runPreviousStatus: undefined,
        task: undefined,
        tab: undefined
      }, async () => {
        const commit = (data = {}) => {
          if (token === this.token) {
            this.setState({pending: false, loaded: true, ...data}, () => {
              this.checkTaskNavigation();
            });
          }
        };
        try {
          await uiNavigation.fetch();
          this.runScheduleRequest = new RunSchedules(runId);
          (this.runScheduleRequest.fetch)();
          const {
            stop,
            fetch: reFetch
          } = await fetchRunInfo(runId, commit, {
            preferences,
            dockerRegistries,
            maxNestedRunsToDisplay: MAX_NESTED_RUNS_TO_DISPLAY
          });
          this.stop = stop;
          this.reFetchRunInfo = reFetch;
          commit({runDataLoaded: true});
        } catch (error) {
          commit({error: error.message, runDataLoaded: true});
        }
      });
    } else {
      this.runScheduleRequest = undefined;
      this.setState({
        run: undefined,
        pending: false,
        loaded: false,
        error: undefined,
        nestedRuns: [],
        hasNestedRuns: false,
        totalNestedRuns: 0,
        nestedRunsPending: false,
        showActiveWorkersOnly: false,
        runTasks: [],
        language: undefined,
        tasksCollapsed: false,
        runDataLoaded: false,
        runPreviousStatus: undefined,
        task: undefined,
        tab: undefined
      });
    }
  };
  onTabChanged = (newTab) => this.setState({tab: newTab});

  renderContent = (currentTab) => {
    if (currentTab && currentTab.render && typeof currentTab.render === 'function') {
      return currentTab.render(this.state);
    }
    return null;
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
      runTasks
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
          onRefreshRunInfo={this.reFetchRunInfo}
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
          onTabChange={this.onTabChanged}
        />
        <div
          className={classNames(
            styles.runDetailsContent,
            styles.runDetailsSection,
            'cp-panel',
            'cp-panel-no-hover',
            'cp-panel-borderless'
          )}
        >
          {this.renderContent(tab)}
        </div>
      </div>
    );
  }
}

RunDetails.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object
};

export default RunDetails;
