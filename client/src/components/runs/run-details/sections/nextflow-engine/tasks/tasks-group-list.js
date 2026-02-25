import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Alert, Input} from 'antd';
import { LoadingOutlined } from '@ant-design/icons';
import styles from './nextflow-engine-tasks.css';
import TasksGroup from './tasks-group';
import {
  isRunCompleted,
  NO_DATA_AVAILABLE_COMPLETED_JOB_MESSAGE,
  NO_DATA_AVAILABLE_RUNNING_JOB_MESSAGE
} from '../../../utilities/helpers';

class TasksGroupList extends React.Component {
  state = {filter: ''};

  onChangeFilter = (e) => {
    this.setState({filter: e.target.value});
  };

  render () {
    const {
      className,
      style,
      tasksGroups = [],
      active,
      onActiveChange,
      pending,
      error,
      run
    } = this.props;
    const {
      filter
    } = this.state;
    const completed = isRunCompleted(run);
    const filtered = tasksGroups
      .filter((t) => !filter ||
        filter.trim().length === 0 ||
        t.name.toLowerCase().includes(filter.trim().toLowerCase()));
    return (
      <div
        className={classNames(className, styles.tasksGroups)}
        style={style}
      >
        <div className={classNames(styles.tasksGroupListHeader)}>
          <span style={{fontWeight: 'bold', flexShrink: 0}}>
            Processes
          </span>
          {
            pending && tasksGroups.length === 0 && (
              <LoadingOutlined style={{marginLeft: 5}} />
            )
          }
          {
            tasksGroups.length > 0 && (
              <span style={{marginLeft: 5, flexShrink: 0}}>
                {
                  filtered.length < tasksGroups.length
                    ? `(${filtered.length} / ${tasksGroups.length})`
                    : `(${tasksGroups.length})`
                }
              </span>
            )
          }
          <div style={{flex: 1, marginLeft: 5}}>
            <Input
              size="small"
              placeholder="Filter processes"
              value={filter}
              onChange={this.onChangeFilter}
            />
          </div>
        </div>
        <div className={classNames(styles.tasksGroupList)}>
          {
            filtered.map((tg) => (
              <TasksGroup
                key={tg.key}
                tasksGroup={tg}
                active={tg.key === active}
                onClick={onActiveChange}
              />
            ))
          }
          {
            filtered.length === 0 && filter && filter.trim().length && tasksGroups.length > 0 && (
              <span className="cp-text-not-important">
                Nothing found for <b>{filter}</b>
              </span>
            )
          }
          {
            tasksGroups.length === 0 && !pending && !error && (
              <div
                style={{
                  width: '100%',
                  height: '100%',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}
              >
                <span className="cp-text-not-important">
                  {
                    completed
                      ? NO_DATA_AVAILABLE_COMPLETED_JOB_MESSAGE
                      : NO_DATA_AVAILABLE_RUNNING_JOB_MESSAGE
                  }
                </span>
              </div>
            )
          }
          {
            tasksGroups.length === 0 && !pending && error && (
              <div
                style={{
                  width: '100%',
                  height: '100%',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}
              >
                <Alert message={error} type="warning" showIcon />
              </div>
            )
          }
          {
            tasksGroups.length === 0 && pending && (
              <div
                style={{
                  width: '100%',
                  height: '100%',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}
              >
                <span className="cp-text-not-important">
                  <LoadingOutlined style={{marginRight: 5}} />
                  <span>Loading processes...</span>
                </span>
              </div>
            )
          }
        </div>
      </div>
    );
  }
}

TasksGroupList.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  tasksGroups: PropTypes.oneOfType([PropTypes.object, PropTypes.array]),
  active: PropTypes.string,
  onActiveChange: PropTypes.func,
  pending: PropTypes.bool,
  error: PropTypes.string,
  run: PropTypes.object
};

export default TasksGroupList;
