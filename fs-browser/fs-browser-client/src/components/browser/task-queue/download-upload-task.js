import React from 'react';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import classNames from 'classnames';
import StatusIcon from './status-icon';
import itemName from './item-name';
import Icon from '../../shared/icon';
import autoDownloadFile from '../../../models/utilities/auto-download-file';
import {TaskStatuses} from '../../../models';
import styles from './task-queue.css';

function getTaskStatus(task) {
  if (task.loaded) {
    return task.value.status;
  }
  if (task.isRunning) {
    return TaskStatuses.pending;
  }
  if (task.error) {
    return TaskStatuses.failure;
  }
  return TaskStatuses.pending;
}

@inject('messages')
@observer
class DownloadUploadTask extends React.Component {
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

  get isDownloadable() {
    const {task} = this.props;
    return task && task.item && task.item.type === 'download' && task.downloadUrl && !task.activeSession;
  }

  get isUploaded() {
    const {task} = this.props;
    return task && task.item && task.item.type === 'upload' && task.value.status === TaskStatuses.success;
  }

  get defaultActionDescription() {
    const {task} = this.props;
    if (task.error && !task.isRunning) {
      return task.error;
    }
    if (task && task.item) {
      switch (task.item.type) {
        case 'download': return 'Downloading...';
        case 'upload': return 'Uploading...';
        default:
          return null;
      }
    }
    return null;
  }

  download = () => {
    const {manager, task} = this.props;
    autoDownloadFile(itemName(task.item.path), task.downloadUrl);
    if (manager) {
      manager.removeTask(task);
    }
  };

  clear = async () => {
    const {manager, messages, task} = this.props;
    if (manager) {
      const error = await manager.cancelTask(task);
      if (error) {
        messages.error(error, 5);
      }
    }
  };

  renderAction = () => {
    if (this.isDownloadable) {
      return (
        <span
          className={styles.download}
        >
          Download
        </span>
      );
    }
    if (this.isUploaded) {
      return (
        <span
          className={styles.upload}
        >
          Uploaded
        </span>
      );
    }
    const description = this.defaultActionDescription;
    if (description) {
      return (
        <span
          className={styles.description}
        >
          {description}
        </span>
      );
    }
    return null;
  };

  render() {
    const {task} = this.props;
    if (!task || !task.item || (task.downloadUrl && task.activeSession)) {
      return null;
    }
    if (task.item
      && task.item.type === 'download'
      && (task.downloadUrl && task.activeSession)) {
      return null;
    }
    return (
      <div
        className={
          classNames(
            styles.task,
            {
              [styles.downloadable]: this.isDownloadable,
            },
          )
        }
      >
        {/* eslint-disable-next-line */}
        <div
          className={styles.nameContainer}
          onClick={task.downloadUrl && !task.activeSession ? this.download : null}
        >
          <div
            className={styles.name}
          >
            <StatusIcon
              status={getTaskStatus(task)}
            />
            {itemName(task.item.path)}
          </div>
          {
            this.renderAction()
          }
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

export default DownloadUploadTask;
