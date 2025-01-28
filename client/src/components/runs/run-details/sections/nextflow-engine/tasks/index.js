import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './nextflow-engine-tasks.css';
import {NextflowProcessesLoader, NextflowTasksLoader} from './loaders';
import TasksGroupList from './tasks-group-list';
import TasksStatuses from './tasks-statuses';
import TasksTable, {TASKS_TABLE_PAGE_SIZE} from './tasks-table';

class NextflowEngineTasks extends React.Component {
  state = {
    processes: [],
    pending: false,
    error: undefined,
    tasksPending: false,
    tasksError: undefined,
    activeTasksGroup: undefined,
    page: 0,
    totalTasksCount: 0,
    filteredTasksCount: 0,
    tasksFilter: undefined,
    tasksSorting: undefined
  };

  componentDidMount () {
    this.onChangeRun();
    this.onChangeTasksFilter(undefined);
  }

  componentDidUpdate (prevProps) {
    const {run: prevRun = {}} = prevProps;
    const {run = {}} = this.props;
    if (prevRun.id !== run.id) {
      this.onChangeRun();
      this.onChangeTasksFilter(undefined);
    } else if (prevRun.status !== run.status) {
      this.onChangeRun();
    }
  }

  componentWillUnmount () {
    if (this.processesLoader) {
      this.processesLoader.destroy();
      this.processesLoader = undefined;
    }
    if (this.tasksLoader) {
      this.tasksLoader.destroy();
      this.tasksLoader = undefined;
    }
  }

  onChangeRun = () => {
    const {run = {}} = this.props;
    const {
      id,
      status
    } = run;
    if (this.processesLoader) {
      this.processesLoader.destroy();
      this.processesLoader = undefined;
    }
    if (id) {
      this.processesLoader = new NextflowProcessesLoader(id, {reload: /^running$/i.test(status)});
      this.processesLoader.addListener(this.onProcessesUpdated);
      this.onChangeTasksFilter(undefined);
    }
  };

  onProcessesUpdated = (processesData) => {
    const {
      pending,
      error,
      processes,
      totalTasks
    } = processesData;
    this.setState({
      pending,
      error,
      processes,
      totalTasksCount: totalTasks
    });
  }

  onActiveTasksGroupChange = (activeTasksGroup) => {
    const {activeTasksGroup: current} = this.state;
    if (current === activeTasksGroup) {
      this.setState({activeTasksGroup: undefined}, () => this.onChangeTasksFilter(undefined));
    } else {
      this.setState({activeTasksGroup}, () => this.onChangeTasksFilter(undefined));
    }
  }

  getStatuses = () => {
    const {
      activeTasksGroup,
      processes
    } = this.state;
    const result = [];
    const filteredProcesses = processes
      .filter((pr) => activeTasksGroup ? pr.key === activeTasksGroup : true);
    for (const pr of filteredProcesses) {
      const {
        stats = []
      } = pr;
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

  onTasksUpdated = (tasksPayload) => {
    const {
      pending,
      error,
      tasks = [],
      totalCount = 0
    } = tasksPayload;
    this.setState({
      tasksPending: pending,
      tasksError: error,
      tasks,
      filteredTasksCount: totalCount
    });
  };

  onChangeTasksFilter = (filter) => {
    this.setState({
      tasksFilter: filter
    }, () => this.onChangeTasksPage(0));
  };

  onChangeTasksSorting = (sorting) => {
    this.setState({
      tasksSorting: sorting
    }, () => this.onChangeTasksPage(0));
  }

  onChangeTasksPage = (page) => {
    this.setState({
      page
    }, this.loadTasks);
  };

  loadTasks = () => {
    const {run} = this.props;
    if (this.tasksLoader) {
      this.tasksLoader.destroy();
      this.tasksLoader = undefined;
    }
    if (run) {
      const {
        id,
        status
      } = run;
      const {
        activeTasksGroup,
        tasksFilter,
        tasksSorting,
        page
      } = this.state;
      this.tasksLoader = new NextflowTasksLoader(
        id,
        {
          reload: /^running$/i.test(status),
          tasksGroup: activeTasksGroup,
          page,
          pageSize: TASKS_TABLE_PAGE_SIZE,
          filters: tasksFilter,
          sorting: tasksSorting
        });
      this.tasksLoader.addListener(this.onTasksUpdated);
    }
  };

  render () {
    const {
      className,
      style
    } = this.props;
    const {
      processes = [],
      activeTasksGroup,
      tasks,
      tasksPending,
      page,
      tasksFilter,
      tasksSorting,
      totalTasksCount,
      filteredTasksCount
    } = this.state;
    const cardClassNames = undefined;
    const statuses = this.getStatuses();
    return (
      <div
        className={classNames(className, styles.nextflowEngineTasks)}
        style={style}
      >
        <div className={classNames(styles.nextflowProcesses, 'cp-divider light bottom')}>
          <TasksGroupList
            className={classNames(
              styles.nextflowProcessesList,
              cardClassNames,
              'cp-divider light right'
            )}
            tasksGroups={processes}
            active={activeTasksGroup}
            onActiveChange={this.onActiveTasksGroupChange}
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
          pending={tasksPending}
          page={page}
          total={totalTasksCount}
          totalFiltered={filteredTasksCount}
          taskGroupFilter={activeTasksGroup}
          onPageChange={this.onChangeTasksPage}
          pageSize={TASKS_TABLE_PAGE_SIZE}
          filter={tasksFilter}
          onFilterChange={this.onChangeTasksFilter}
          sorting={tasksSorting}
          onSortingChange={this.onChangeTasksSorting}
        />
      </div>
    );
  }
}

NextflowEngineTasks.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object
};

export default NextflowEngineTasks;
