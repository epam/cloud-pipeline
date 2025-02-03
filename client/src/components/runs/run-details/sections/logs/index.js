import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Button, Icon, Input, message} from 'antd';
import {inject} from 'mobx-react';
import styles from './run-logs.css';
import SplitPane from 'react-split-pane';
import RunTaskLogs from '../../../run-task-logs';
import RunTask from './run-task';
import {checkRunActionAvailable, runActions} from '../../../actions/actions-availability';
import PipelineExportLog from '../../../../../models/pipelines/PipelineExportLog';
import FileSaver from 'file-saver';
import LoadingView from '../../../../special/LoadingView';

const DEFAULT_TASKS_LIST_WIDTH = 300;

@inject('uiNavigation', 'preferences')
class RunLogsSection extends React.Component {
  state = {
    task: undefined,
    defaultSize: 0,
    size: undefined,
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
      run,
      loaded
    } = this.props;
    if (!run || !loaded) {
      return;
    }
    // `task` holds current selected run task
    let {
      task
    } = this.state;
    const {
      defaultSize: currentTasksListDefaultSize,
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
    let defaultSize = currentTasksListDefaultSize;
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
        defaultSize = 0;
      } else if (consoleTask) {
        // run doesn't have finished "pipeline" task - we should navigate to console task
        taskToNavigate = consoleTask;
        defaultSize = 0;
      }
    } else if (!uiNavigation.runLogsMainTask) {
      defaultSize = DEFAULT_TASKS_LIST_WIDTH;
    }
    if (runPreviousStatus !== status || currentTasksListDefaultSize !== defaultSize) {
      this.setState({
        defaultSize,
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

  getCurrentSize = () => {
    const {size, defaultSize} = this.state;
    return size === undefined ? defaultSize : size;
  }

  toggleTasksCollapsed = () => {
    const size = this.getCurrentSize();
    this.setState({
      size: size > 0 ? 0 : DEFAULT_TASKS_LIST_WIDTH
    });
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
            type={timings ? 'primary' : undefined}
            style={{marginLeft: 5}}
            className={styles.runLogsSectionButton}
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

  exportLog = async () => {
    const {run} = this.props;
    if (!run) {
      return;
    }
    try {
      const hide = message.loading('Exporting log...');
      const request = new PipelineExportLog(run.id);
      await request.fetch();
      if (request.response) {
        FileSaver.saveAs(request.response, `run_${run.id}_log.txt`);
      } else {
        message.error('Error exporting log', 2);
      }
      hide();
    } catch (e) {
      message.error('Error exporting log', 5);
    }
  };

  onSplitPanelSizeChange = (size) => this.setState({size});

  render () {
    const {
      className,
      style,
      run,
      loaded,
      pending
    } = this.props;
    if (!run) {
      return null;
    }
    const {task} = this.state;
    const {
      status
    } = run || {};
    const size = this.getCurrentSize();
    return (
      <div
        className={classNames(className, styles.runLogsSection)}
        style={style}
      >
        {
          loaded && (
            <div className={styles.runLogsSectionHeader}>
              <a onClick={this.toggleTasksCollapsed}>
                {size === 0 ? 'Show tasks' : 'Hide tasks'}
              </a>
              <div style={{marginLeft: 'auto'}} className={styles.runLogsSectionActions}>
                {
                  !/^running$/i.test(status) &&
                  checkRunActionAvailable(run, runActions.exportLogs) && (
                    <Button
                      size="small"
                      className={styles.runLogsSectionButton}
                      onClick={this.exportLog}
                    >
                      Export logs
                    </Button>
                  )
                }
              </div>
            </div>
          )
        }
        {
          loaded && (
            <div className={styles.runLogsSectionSplitPanel}>
              <SplitPane
                style={{display: 'flex', flex: 1, minHeight: 500}}
                minSize={0}
                size={size}
                onChange={this.onSplitPanelSizeChange}
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
          )
        }
        {
          !loaded && pending && (
            <LoadingView />
          )
        }
      </div>
    );
  }
}

RunLogsSection.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object,
  runTasks: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  pending: PropTypes.bool,
  loaded: PropTypes.bool
};

export default RunLogsSection;
