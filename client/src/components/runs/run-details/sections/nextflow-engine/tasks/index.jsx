import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './nextflow-engine-tasks.module.css';
import {NextflowTasksLoader} from './loaders';
import TasksGroupList from './tasks-group-list';
import TasksStatuses from './tasks-statuses';
import {Modal} from 'antd';
import TasksTable, {TASKS_TABLE_PAGE_SIZE} from './tasks-table';
import TaskDetails from '../task-details/task-details';

class NextflowEngineTasks extends React.Component {
  state = {
    processes: [],
    pending: false,
    error: undefined,
    tasksError: undefined,
    activeTasksGroup: undefined,
    loadedTasksGroup: undefined,
    page: 0,
    totalTasksCount: 0,
    filteredTasksCount: 0,
    tasksFilter: undefined,
    tasksSorting: undefined,
    taskDetails: undefined,
  };

  componentDidMount() {
    this.updateData(true);
  }

  componentDidUpdate(prevProps) {
    const {run: prevRun = {}} = prevProps;
    const {run = {}} = this.props;
    if (prevRun.id !== run.id || prevRun.status !== run.status) {
      this.updateData(prevRun.id !== run.id);
    }
  }

  componentWillUnmount() {
    if (this.tasksLoader) {
      this.tasksLoader.destroy();
      this.tasksLoader = undefined;
    }
  }

  updateData = (runChanged = false) => {
    const {run} = this.props;
    if (runChanged) {
      this.setState({
        processes: [],
        pending: false,
        error: undefined,
        tasksError: undefined,
        activeTasksGroup: undefined,
        loadedTasksGroup: undefined,
        page: 0,
        totalTasksCount: 0,
        filteredTasksCount: 0,
        tasksFilter: undefined,
        tasksSorting: undefined,
        taskDetails: undefined,
      });
      if (this.tasksLoader) {
        this.tasksLoader.destroy();
        this.tasksLoader = undefined;
      }
    }
    if (run) {
      const {id, status} = run;
      const {activeTasksGroup, tasksFilter, tasksSorting, page} = this.state;
      const taskLoaderOptions = {
        reload: /^running$/i.test(status),
        tasksGroup: activeTasksGroup,
        page,
        pageSize: TASKS_TABLE_PAGE_SIZE,
        filters: tasksFilter,
        sorting: tasksSorting,
      };
      if (!this.tasksLoader) {
        this.tasksLoader = new NextflowTasksLoader(id, taskLoaderOptions);
        this.tasksLoader.addListener(this.onDataUpdated);
      } else {
        this.tasksLoader.setOptions(taskLoaderOptions);
      }
    } else {
      if (this.tasksLoader) {
        this.tasksLoader.destroy();
        this.tasksLoader = undefined;
      }
      this.setState({
        processes: [],
        pending: false,
        error: undefined,
        tasksError: undefined,
        activeTasksGroup: undefined,
        loadedTasksGroup: undefined,
        page: 0,
        totalTasksCount: 0,
        filteredTasksCount: 0,
        tasksFilter: undefined,
        tasksSorting: undefined,
        taskDetails: undefined,
      });
    }
  };

  onDataUpdated = (data) => {
    const {
      pending = false,
      processes = [],
      tasks = [],
      totalTasksCount = 0,
      filteredTasksCount = 0,
      error,
      tasksError,
      loadedTasksGroup,
    } = data || {};
    this.setState({
      pending,
      processes,
      tasks,
      totalTasksCount,
      filteredTasksCount,
      error,
      tasksError,
      loadedTasksGroup,
    });
  };

  onActiveTasksGroupChange = (activeTasksGroup) => {
    const {activeTasksGroup: current} = this.state;
    if (current === activeTasksGroup) {
      this.setState({activeTasksGroup: undefined}, () => this.onChangeTasksFilter(undefined));
    } else {
      this.setState({activeTasksGroup}, () => this.onChangeTasksFilter(undefined));
    }
  };

  getStatuses = () => {
    const {activeTasksGroup, processes = []} = this.state;
    const result = [];
    const filteredProcesses = processes.filter((pr) =>
      activeTasksGroup ? pr.key === activeTasksGroup : true,
    );
    for (const pr of filteredProcesses) {
      const {stats = []} = pr;
      for (const stat of stats) {
        let current = result.find((o) => o.status === stat.status);
        if (!current) {
          current = {...stat, count: 0};
          result.push(current);
        }
        current.count += stat.count;
      }
    }
    return result;
  };

  onChangeTasksFilter = (filter) => {
    this.setState(
      {
        tasksFilter: filter,
      },
      () => this.updateData(),
    );
  };

  onChangeTasksSorting = (sorting) => {
    this.setState(
      {
        tasksSorting: sorting,
      },
      () => this.onChangeTasksPage(0),
    );
  };

  onChangeTasksPage = (page) => {
    this.setState(
      {
        page,
      },
      this.updateData,
    );
  };

  onTaskClick = (task) => this.setState({taskDetails: task});

  closeTaskDetails = () => this.onTaskClick(undefined);

  render() {
    const {className, style, run} = this.props;
    const {
      pending,
      error,
      processes = [],
      activeTasksGroup,
      loadedTasksGroup,
      tasks,
      page,
      tasksFilter,
      tasksSorting,
      totalTasksCount,
      filteredTasksCount,
      taskDetails,
      tasksError,
    } = this.state;
    const {status} = run || {};
    const cardClassNames = undefined;
    const statuses = this.getStatuses();
    return (
      <div className={classNames(className, styles.nextflowEngineTasks)} style={style}>
        <div className={classNames(styles.nextflowProcesses, 'cp-divider light bottom')}>
          <TasksGroupList
            className={classNames(
              styles.nextflowProcessesList,
              cardClassNames,
              'cp-divider light right',
            )}
            tasksGroups={processes}
            active={activeTasksGroup}
            onActiveChange={this.onActiveTasksGroupChange}
            pending={pending}
            error={error}
            run={run}
          />
          <TasksStatuses
            className={classNames(styles.nextflowProcessesChart, cardClassNames)}
            statuses={statuses}
            taskGroupFilter={activeTasksGroup}
          />
        </div>
        <TasksTable
          className={classNames(styles.nextflowTasks, cardClassNames)}
          tasks={tasks}
          pending={pending}
          error={tasksError}
          page={page}
          run={run}
          total={totalTasksCount}
          totalFiltered={filteredTasksCount}
          taskGroupFilter={loadedTasksGroup}
          onPageChange={this.onChangeTasksPage}
          pageSize={TASKS_TABLE_PAGE_SIZE}
          filter={tasksFilter}
          onFilterChange={this.onChangeTasksFilter}
          sorting={tasksSorting}
          onSortingChange={this.onChangeTasksSorting}
          onTaskClick={this.onTaskClick}
        />
        <Modal
          title={false}
          footer={false}
          onCancel={this.closeTaskDetails}
          open={taskDetails !== undefined}
          width="90%"
          className="cp-run-engine-task-modal"
        >
          <div className={styles.taskDetailsModalBody}>
            {taskDetails && (
              <TaskDetails task={taskDetails} reload={/^running$/i.test(status)} run={run} />
            )}
          </div>
        </Modal>
      </div>
    );
  }
}

NextflowEngineTasks.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object,
};

export default NextflowEngineTasks;
