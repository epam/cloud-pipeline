import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Button, Icon, Input} from 'antd';
import {inject} from 'mobx-react';
import styles from './run-logs.css';
import SplitPane from 'react-split-pane';
import RunTaskLogs from '../../../run-task-logs';
import RunTask from './run-task';

@inject('uiNavigation', 'preferences')
class RunLogsSection extends React.Component {
  state = {
    task: undefined,
    tasksCollapsed: false,
    runPreviousStatus: undefined,
    timings: false,
    searchTasks: ''
  };

  componentDidMount () {
    this.checkTaskNavigation();
  }

  componentDidUpdate () {
    this.checkTaskNavigation();
  }

  /**
   * Checks if the form should be navigated to a specific task
   */
  checkTaskNavigation = () => {
    const {
      uiNavigation,
      runTasks = [],
      run
    } = this.props;
    if (!run) {
      return;
    }
    // `task` holds current selected run task
    let {
      task
    } = this.state;
    const {
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

  switchTimings = () => {
    this.setState({timings: !this.state.timings});
  };

  renderTasksList = () => {
    const {
      runTasks = [],
      pending
    } = this.props;
    const {searchTasks, timings, task: currentTask} = this.state;
    const onSearchTasksChanged = (e) => {
      this.setState({searchTasks: e.target.value});
    };
    const filteredTasks = runTasks
      .filter(task => searchTasks
        ? (task.name || '').toLowerCase().includes((searchTasks || '').toLowerCase())
        : true
      );
    const onSelectTask = (task) => this.setState({task});
    return (
      <div className={styles.runTasksListContainer}>
        <div className={styles.runTasksSearch}>
          <Input
            size="small"
            value={searchTasks}
            onChange={onSearchTasksChanged}
            style={{flex: 1}}
            placeholder="Filter tasks"
          />
          <Button
            onClick={this.switchTimings}
            type={timings ? 'primary' : undefined} style={{marginLeft: 5}}
            size="small"
            title={timings ? 'Hide tasks timings' : 'Show tasks timings'}
          >
            <Icon
              style={{fontSize: '0.8rem'}}
              type={timings ? 'clock-circle' : 'clock-circle-o'}
            />
          </Button>
        </div>
        <div className={styles.runTasksList}>
          {
            pending && runTasks.length === 0 && (
              <span className="cp-text-not-important">
                Loading tasks...
              </span>
            )
          }
          {
            !pending && runTasks.length === 0 && (
              <span className="cp-text-not-important">
                No tasks
              </span>
            )
          }
          {
            runTasks.length > 0 && filteredTasks.length === 0 && (
              <span className="cp-text-not-important">
                Nothing found for <b>{searchTasks}</b>
              </span>
            )
          }
          {
            filteredTasks.map((task, index) => (
              <RunTask
                task={task}
                key={`${task.name}-${index}`}
                timings={timings}
                searchText={searchTasks}
                active={currentTask && currentTask.name === task.name}
                onTaskClick={onSelectTask}
              />
            ))
          }
        </div>
      </div>
    );
  };

  render () {
    const {
      className,
      style,
      run
    } = this.props;
    if (!run) {
      return null;
    }
    const {task} = this.state;
    const {
      status
    } = run || {};

    const {tasksCollapsed, runDataLoaded} = this.state;
    const collapse = tasksCollapsed || !runDataLoaded;

    return (
      <div className={classNames(className, styles.runLogsSection)} style={style}>
        <SplitPane
          style={{display: 'flex', flex: 1, minHeight: 500}}
          defaultSize={collapse ? 0 : 300}
          minSize={collapse ? 0 : 100}
          pane1Style={{display: 'flex', flexDirection: 'column'}}
          pane2Style={{display: 'flex', flexDirection: 'column'}}
          resizerClassName="cp-split-panel-resizer"
          resizerStyle={{
            width: 8,
            margin: 0,
            cursor: 'col-resize',
            boxSizing: 'border-box',
            backgroundClip: 'padding',
            zIndex: 1
          }}>
          {this.renderTasksList()}
          <RunTaskLogs
            className={styles.logs}
            runId={run.id}
            taskName={task ? task.name : undefined}
            taskParameters={task ? task.parameters : undefined}
            taskInstance={task ? task.instance : undefined}
            autoUpdate={/^(running|pausing|resuming)$/i.test(status)}
            fetchAllLogs={false}
          />
        </SplitPane>
      </div>
    );
  }
}

RunLogsSection.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object,
  runTasks: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  pending: PropTypes.bool
};

export default RunLogsSection;
