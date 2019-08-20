import React from 'react';
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import {message} from 'antd';
import classNames from 'classnames';
import StatusIcon from './status-icon';
import itemName from './item-name';
import Icon from '../../shared/icon';
import styles from './task-queue.css';
import {TaskStatuses} from '../../../models';

@observer
class UploadTask extends React.Component {
  static propTypes = {
    // eslint-disable-next-line
    manager: PropTypes.object,
    // eslint-disable-next-line
    task: PropTypes.object,
  };

  static defaultProps = {
    manager: null,
    task: null,
  };

  getTaskStatus = () => {
    const {task} = this.props;
    if (!task || task.pending) {
      return TaskStatuses.running;
    }
    if (task.error) {
      return TaskStatuses.failure;
    }
    return TaskStatuses.success;
  };

  clear = async () => {
    const {manager, task} = this.props;
    if (manager) {
      const error = await manager.cancelTask(task);
      if (error) {
        message.error(error, 5);
      }
    }
  };

  render() {
    const {task} = this.props;
    if (!task || !task.item) {
      return null;
    }
    return (
      // eslint-disable-next-line
      <div
        className={
          classNames(
            styles.task,
          )
        }
      >
        {/* eslint-disable-next-line */}
        <div
          className={styles.nameContainer}
        >
          <div
            className={styles.name}
          >
            <StatusIcon
              status={this.getTaskStatus()}
            />
            {itemName(task.item.path)}
          </div>
        </div>
        <Icon
          type="close"
          className={styles.clear}
          onClick={this.clear}
        />
      </div>
    );
  }
}

export default UploadTask;
