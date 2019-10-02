import React from 'react';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import classNames from 'classnames';
import StatusIcon from './status-icon';
import Icon from '../../shared/icon';
import styles from './task-queue.css';
import {TaskStatuses} from '../../../models';

@inject('messages')
@observer
class UploadToBucketTask extends React.Component {
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

  get status() {
    const {task} = this.props;
    if (task && task.error) {
      return TaskStatuses.failure;
    }
    return TaskStatuses.pending;
  }

  clear = async () => {
    const {manager, messages, task} = this.props;
    if (manager) {
      const error = await manager.cancelTask(task);
      if (error) {
        messages.error(error, 5);
      }
    }
  };

  renderProgress = () => {
    const {task} = this.props;
    if (!task || !task.item) {
      return null;
    }
    return (
      <div
        className={
          classNames(
            styles.progress,
            {
              [styles.done]: task.loaded,
              [styles.error]: !!task.error,
            },
          )
        }
        style={{width: `${task.percent * 100}%`}}
      >
        {'\u00A0'}
      </div>
    );
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
        {this.renderProgress()}
        {/* eslint-disable-next-line */}
        <div
          className={styles.nameContainer}
        >
          <div
            className={styles.name}
          >
            <StatusIcon
              status={this.status}
            />
            {task.name}
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

export default UploadToBucketTask;
