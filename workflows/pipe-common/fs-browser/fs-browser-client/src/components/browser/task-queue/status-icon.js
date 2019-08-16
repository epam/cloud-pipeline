import React from 'react';
import {TaskStatuses} from '../../../models';
import Icon from '../../shared/icon';

export default function ({status}) {
  if (!status) {
    return null;
  }
  let icon = null;
  switch (status) {
    case TaskStatuses.success:
      icon = 'check';
      break;
    case TaskStatuses.failure:
    case TaskStatuses.cancelled:
      icon = 'warning';
      break;
    default:
      icon = 'loading';
      break;
  }
  return (
    <Icon type={icon} />
  );
}
