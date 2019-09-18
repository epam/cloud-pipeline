import React from 'react';
import DownloadUploadTask from './download-upload-task';
import UploadToBucketTask from './upload-to-bucket-task';

export default function ({manager, task}) {
  if (!task || !task.item) {
    return null;
  }
  if (task.item.type === 'download' || task.item.type === 'upload') {
    return (
      <DownloadUploadTask manager={manager} task={task} />
    );
  }
  if (task.item.type === 'upload-to-bucket') {
    return (
      <UploadToBucketTask manager={manager} task={task} />
    );
  }
  return null;
}
