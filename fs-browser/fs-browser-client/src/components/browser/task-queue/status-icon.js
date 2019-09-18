import React from 'react';
import {TaskStatuses} from '../../../models';
import Icon from '../../shared/icon';

export default function ({status}) {
  if (!status) {
    return null;
  }
  let icon = null;
  let color;
  switch (status) {
    case TaskStatuses.success:
      icon = 'check';
      color = 'rgba(88, 173, 82, 1)';
      break;
    case TaskStatuses.failure:
    case TaskStatuses.cancelled:
      icon = 'warning';
      color = 'rgb(255, 106, 0)';
      break;
    default:
      icon = 'loading';
      break;
  }
  return (
    <Icon
      color={color}
      type={icon}
    />
  );
}
