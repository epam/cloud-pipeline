import React from 'react';
import {
  Button,
  Icon,
  message,
} from 'antd';
import {inject, observer} from 'mobx-react';
import styles from '../browser.css';

function download(
  {
    disabled,
    item,
    taskManager,
    callback,
  },
) {
  if (!item.downloadable) {
    return null;
  }
  const [downloadTask] = taskManager.getTasksByPath(item.path)
    .filter(i => i.item.type === 'download');
  const isDownloading = downloadTask && downloadTask.isRunning;
  const onClick = async (e) => {
    e.stopPropagation();
    const hide = message.loading('Initializing download process...', 0);
    const {error, value} = await taskManager.download(item.path);
    hide();
    if (error) {
      message.error(error, 5);
    } else if (callback) {
      callback(item, taskManager.getTaskById(value.task));
    }
  };
  if (isDownloading) {
    return (
      <span
        className={styles.action}
      >
        <Icon type="loading" />
      </span>
    );
  }
  return (
    <span
      className={styles.action}
    >
      <Button
        disabled={disabled}
        size="small"
        onClick={onClick}
        type="link"
        title={`Download ${item.name}`}
      >
        <Icon type="download" />
      </Button>
    </span>
  );
}

export default inject('taskManager')(observer(download));
