import React from 'react';
import {
  Button,
  Modal,
  message,
} from 'antd';
import Icon from '../../shared/icon';
import styles from '../browser.css';
import {DeletePath} from '../../../models';

function remove(
  {
    disabled,
    item,
    callback,
  },
) {
  if (!item.removable) {
    return null;
  }
  const onClick = async (e) => {
    e.stopPropagation();
    const onOk = async () => {
      const hide = message.loading(`Removing "${item.path}"`, 0);
      const request = new DeletePath(item.path);
      await request.fetch();
      hide();
      if (request.error) {
        message.error(request.error, 5);
      } else if (callback) {
        callback(item);
      }
    };
    Modal.confirm({
      title: `Are you sure you want to delete ${item.type.toLowerCase()} "${item.name}"?`,
      onOk,
    });
  };
  return (
    <span
      className={styles.action}
    >
      <Icon
        disabled={disabled}
        type="close"
        color="red"
        onClick={onClick}
      />
    </span>
  );
}

export default remove;
