import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Button, Input, message, Splitter} from 'antd';
import {ClockCircleFilled, ClockCircleOutlined} from '@ant-design/icons';
import {inject} from 'mobx-react';
import styles from './run-logs.module.css';
import RunTaskLogs from '../../../run-task-logs';
import RunTask from './run-task';
import {checkRunActionAvailable, runActions} from '../../../actions/actions-availability';
import PipelineExportLog from '../../../../../models/pipelines/PipelineExportLog';
import {downloadBlob} from '../../../../../utils/download-blob';
import LoadingView from '../../../../special/LoadingView.tsx';

const DEFAULT_TASKS_LIST_WIDTH = 300;

@inject('uiNavigation', 'preferences')
class RunLogsSection extends React.Component {
  state = {
    task: undefined,
    defaultSize: 0,
    size: undefined,
    runPreviousStatus: undefined,
    timings: false,
    searchTasks: '',
  };

  componentDidMount() {
    this.checkTaskNavigation();
  }

  componentDidUpdate() {
    this.checkTaskNavigation();
  }

  /**
   * Checks if the form should be navigated to a specific task
   */
  checkTaskNavigation = () => {
    const {uiNavigation, runTasks = [], run, loaded} = this.props;
    if (!run || !loaded) {
      return;
    }
    // `task` holds current selected run task
    let {task} = this.state;
    const {defaultSize: currentTasksListDefaultSize, runPreviousStatus} = this.state;
    let taskToNavigate;
    if (!task && runTasks.length > 0) {
      // If no task is selected and there are some tasks in run -
      // we need to navigate to any of it
      taskToNavigate = runTasks[0];
    }
    const {pipelineName, podId, status} = run;
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
      const pipelineTask = runTasks.find(
        (t) => (t.name || '').toLowerCase() === pipelineTaskName.toLowerCase(),
      );
      const consoleTask = runTasks.find((t) => (t.name || '').toLowerCase() === 'console');
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
        runPreviousStatus: status,
      });
    }
    if (
      taskToNavigate &&
      (!task || task.name.toLowerCase() !== taskToNavigate.name.toLowerCase())
    ) {
      // there is a task to be navigated to (`taskToNavigate`)
      // and current selected task either is missing or differs from the `taskToNavigate`
      this.setState({
        task: taskToNavigate,
      });
    }
  };

  switchTimings = () => {
    this.setState({timings: !this.state.timings});
  };

  getCurrentSize = () => {
    const {size, defaultSize} = this.state;
    return size === undefined ? defaultSize : size;
  };

  toggleTasksCollapsed = () => {
    const size = this.getCurrentSize();
    this.setState({
      size: size > 0 ? 0 : DEFAULT_TASKS_LIST_WIDTH,
    });
  };

  renderTasksList = () => {
    const {runTasks = [], pending} = this.props;
    const {searchTasks, timings, task: currentTask} = this.state;
    const onSearchTasksChanged = (e) => {
      this.setState({searchTasks: e.target.value});
    };
    const filteredTasks = runTasks.filter((task) =>
      searchTasks
        ? (task.name || '').toLowerCase().includes((searchTasks || '').toLowerCase())
        : true,
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
            {timings ? (
              <ClockCircleFilled style={{fontSize: '0.8rem'}} />
            ) : (
              <ClockCircleOutlined style={{fontSize: '0.8rem'}} />
            )}
          </Button>
        </div>
        <div className={styles.runTasksList}>
          {pending && runTasks.length === 0 && (
            <span className="cp-text-not-important">Loading tasks...</span>
          )}
          {!pending && runTasks.length === 0 && (
            <span className="cp-text-not-important">No tasks</span>
          )}
          {runTasks.length > 0 && filteredTasks.length === 0 && (
            <span className="cp-text-not-important">
              Nothing found for <b>{searchTasks}</b>
            </span>
          )}
          {filteredTasks.map((task, index) => (
            <RunTask
              task={task}
              key={`${task.name}-${index}`}
              timings={timings}
              searchText={searchTasks}
              active={currentTask && currentTask.name === task.name}
              onTaskClick={onSelectTask}
            />
          ))}
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
        downloadBlob(request.response, `run_${run.id}_log.txt`);
      } else {
        message.error('Error exporting log', 2);
      }
      hide();
    } catch (e) {
      message.error('Error exporting log', 5);
    }
  };

  onSplitPanelSizeChange = (sizes) => this.setState({size: sizes[0]});

  get pipelineTaskName() {
    const {run = {}} = this.props;
    const {pipelineName, podId} = run;
    return pipelineName || podId || undefined;
  }

  get jobIsRunning() {
    const {run = {}} = this.props;
    const {status} = run;
    return ['RUNNING', 'PAUSED', 'PAUSING', 'RESUMING'].includes(status);
  }

  get showLogsDate() {
    const {task} = this.state;
    const {pipelineTaskName} = this;
    if (
      task &&
      (/^console$/i.test(task.name) ||
        (!this.jobIsRunning &&
          pipelineTaskName &&
          pipelineTaskName.toLowerCase() === task.name.toLowerCase()))
    ) {
      return false;
    }
    return undefined;
  }

  render() {
    const {className, style, run, loaded, pending} = this.props;
    if (!run) {
      return null;
    }
    const {task, defaultSize} = this.state;
    const {status} = run || {};
    const size = this.getCurrentSize();
    return (
      <div className={classNames(className, styles.runLogsSection)} style={style}>
        {loaded && (
          <div className={styles.runLogsSectionHeader}>
            <a onClick={this.toggleTasksCollapsed}>{size === 0 ? 'Show tasks' : 'Hide tasks'}</a>
            <div style={{marginLeft: 'auto'}} className={styles.runLogsSectionActions}>
              {!/^running$/i.test(status) &&
                checkRunActionAvailable(run, runActions.exportLogs) && (
                  <Button
                    size="small"
                    className={styles.runLogsSectionButton}
                    onClick={this.exportLog}
                  >
                    Export logs
                  </Button>
                )}
            </div>
          </div>
        )}
        {loaded && (
          <div className={styles.runLogsSectionSplitPanel}>
            <Splitter
              style={{display: 'flex', flex: 1, minHeight: 500}}
              onResize={this.onSplitPanelSizeChange}
            >
              <Splitter.Panel
                style={{display: 'flex', flexDirection: 'column'}}
                defaultSize={defaultSize}
                size={size}
                collapsible
              >
                {this.renderTasksList()}
              </Splitter.Panel>
              <Splitter.Panel style={{display: 'flex', flexDirection: 'column'}}>
                <RunTaskLogs
                  className={styles.logs}
                  runId={run.id}
                  taskName={task ? task.name : undefined}
                  showDate={this.showLogsDate}
                  taskParameters={task ? task.parameters : undefined}
                  taskInstance={task ? task.instance : undefined}
                  autoUpdate={/^(running|pausing|resuming)$/i.test(status)}
                  fetchAllLogs={false}
                />
              </Splitter.Panel>
            </Splitter>
          </div>
        )}
        {!loaded && pending && <LoadingView />}
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
  loaded: PropTypes.bool,
};

export default RunLogsSection;
