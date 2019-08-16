import React from 'react';
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import classNames from 'classnames';
import StatusIcon from './status-icon';
import autoDownloadFile from '../../../models/utilities/auto-download-file';
import styles from './task-queue.css';

const itemName = path => (path || '').split('/').pop();

@observer
class DownloadTask extends React.Component {
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

  download = () => {
    const {manager, task} = this.props;
    autoDownloadFile(itemName(task.item.path), task.downloadUrl);
    if (manager) {
      manager.removeTask(task);
    }
  };

  render() {
    const {task} = this.props;
    if (!task || !task.item || (task.downloadUrl && task.activeSession)) {
      return null;
    }
    return (
      // eslint-disable-next-line
      <div
        className={
          classNames(
            styles.task,
            {
              [styles.downloadable]: task.downloadUrl && !task.activeSession,
            },
          )
        }
        onClick={task.downloadUrl && !task.activeSession ? this.download : null}
      >
        <div
          className={styles.name}
        >
          <StatusIcon
            status={task.loaded ? task.value.status : null}
          />
          {itemName(task.item.path)}
        </div>
        <span
          className={styles.download}
        >
          Download
        </span>
      </div>
    );
  }
}

export default DownloadTask;
