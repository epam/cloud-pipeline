import React from 'react';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import classNames from 'classnames';
import Icon from '../../shared/icon';
import Task from './task';
import styles from './task-queue.css';

@inject('taskManager')
@observer
class TaskQueue extends React.Component {
  static propTypes = {
    activeTasksCount: PropTypes.number,
  };

  static defaultProps = {
    activeTasksCount: 0,
  };

  state = {
    visible: false,
  };

  componentWillReceiveProps(nextProps) {
    const {activeTasksCount} = this.props;
    if (nextProps.activeTasksCount > activeTasksCount) {
      this.setState({visible: true});
    }
  }

  open = () => {
    const {visible} = this.state;
    if (!visible) {
      this.setState({visible: true});
    }
  };

  close = () => {
    this.setState({visible: false});
  };

  render() {
    const {visible} = this.state;
    const {taskManager} = this.props;
    if ((!taskManager.items || taskManager.items.length === 0)) {
      return null;
    }
    return (
      // eslint-disable-next-line
      <div
        className={
          classNames(
            styles.container,
            {[styles.hidden]: !visible},
          )
        }
        onClick={this.open}
      >
        <div className={styles.expand}>
          <Icon
            type="download"
            width={30}
          />
        </div>
        <div
          className={styles.header}
        >
          <span>
            Operations
          </span>
          <Icon
            type="close"
            onClick={this.close}
          />
        </div>
        <div
          className={styles.content}
        >
          {
            taskManager.items
            && taskManager.items.map(item => (
              <Task
                key={item.id}
                manager={taskManager}
                task={item}
              />
            ))
          }
        </div>
      </div>
    );
  }
}

export default TaskQueue;
