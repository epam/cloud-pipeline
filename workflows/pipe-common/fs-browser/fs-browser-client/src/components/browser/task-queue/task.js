import React from 'react';
import DownloadTask from './download-task';
import UploadTask from './upload-task';
import UploadToBucketTask from './upload-to-bucket-task';

export default function ({manager, task}) {
  if (!task || !task.item) {
    return null;
  }
  if (task.item.type === 'download') {
    return (
      <DownloadTask manager={manager} task={task} />
    );
  }
  if (task.item.type === 'upload') {
    return (
      <UploadTask manager={manager} task={task} />
    );
  }
  if (task.item.type === 'upload-to-bucket') {
    return (
      <UploadToBucketTask manager={manager} task={task} />
    );
  }
  return null;
}
