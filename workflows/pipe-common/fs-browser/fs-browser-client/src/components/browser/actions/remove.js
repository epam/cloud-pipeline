import React from 'react';
import {
  Button,
  Icon,
  Modal,
  message,
} from 'antd';
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
      <Button
        disabled={disabled}
        size="small"
        onClick={onClick}
        type="link"
        title={`Remove ${item.name}`}
        style={{color: 'red'}}
      >
        <Icon type="close" />
      </Button>
    </span>
  );
}

export default remove;
