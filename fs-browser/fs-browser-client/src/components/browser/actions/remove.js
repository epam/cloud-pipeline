import React from 'react';
import {inject, observer} from 'mobx-react';
import Icon from '../../shared/icon';
import styles from '../browser.css';
import {DeletePath} from '../../../models';

function remove(
  {
    disabled,
    item,
    callback,
    messages,
  },
) {
  if (!item.removable) {
    return null;
  }
  const onClick = async (e) => {
    e.stopPropagation();
    // eslint-disable-next-line
    if (confirm(`Are you sure you want to delete ${item.type.toLowerCase()} "${item.name}"?`)) {
      const hide = messages.loading(`Removing "${item.path}"`);
      const request = new DeletePath(item.path);
      await request.fetch();
      hide();
      if (request.error) {
        messages.error(request.error, 5);
      } else if (callback) {
        callback(item);
      }
    }
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

export default inject('messages')(observer(remove));
