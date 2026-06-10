import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './task-details.module.css';
import TaskDetailsHeader from './task-details-header';
import TaskDetailsTabs from './task-details-tabs';
import {getTaskDetailsTabs} from './tabs';

class TaskDetails extends React.Component {
  state = {
    activeTab: undefined,
    tabs: [],
  };

  componentDidMount() {
    this.updateFromProps();
  }

  componentDidUpdate(prevProps) {
    const {task: prevTask = {}} = prevProps;
    const {task = {}} = this.props;
    const {runId: prevRunId, taskKey: prevTaskKey} = prevTask;
    const {runId, taskKey} = task;
    if (runId !== prevRunId || taskKey !== prevTaskKey) {
      this.updateFromProps();
    }
  }

  updateFromProps = () => {
    const {task} = this.props;
    this.setState({activeTab: undefined, tabs: getTaskDetailsTabs(task)});
  };

  onChangeActiveTab = (activeTabKey) => this.setState({activeTab: activeTabKey});

  getActiveTab = () => {
    const {activeTab, tabs} = this.state;
    return tabs.find((t) => t.tab === activeTab) || tabs[0];
  };

  render() {
    const {className, style, task, reload, run} = this.props;
    const {tabs} = this.state;
    const activeTab = this.getActiveTab();
    return (
      <div className={classNames(className, styles.taskDetailsContainer)} style={style}>
        <TaskDetailsHeader
          className={classNames(
            styles.taskDetailsSection,
            styles.taskDetailsHeader,
            'cp-panel',
            'cp-panel-no-hover',
            'cp-panel-borderless',
          )}
          task={task}
        />
        <TaskDetailsTabs
          className={classNames(
            styles.taskDetailsSection,
            styles.taskDetailsTabs,
            'cp-panel',
            'cp-panel-no-hover',
            'cp-panel-borderless',
          )}
          task={task}
          tabs={tabs}
          activeTabKey={activeTab ? activeTab.tab : undefined}
          onChangeActiveTabKey={this.onChangeActiveTab}
        />
        {activeTab && typeof activeTab.render === 'function'
          ? activeTab.render(task, {
              className: classNames(
                styles.taskDetailsSection,
                styles.taskDetailsContent,
                'cp-panel',
                'cp-panel-no-hover',
                'cp-panel-borderless',
              ),
              reload,
              run,
            })
          : undefined}
      </div>
    );
  }
}

TaskDetails.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  task: PropTypes.object,
  run: PropTypes.object,
  reload: PropTypes.oneOfType([PropTypes.bool, PropTypes.number]),
};

export default TaskDetails;
