import React from 'react';
import classNames from 'classnames';
import Icon from '../icon';
import styles from './alerts.css';

export default function ({title, type}) {
  return (
    <div
      className={classNames(styles.message, styles[type])}
    >
      <Icon
        className={styles.icon}
        type={type}
        color={type === 'warning' ? 'red' : null}
      />
      <span
        className={styles.title}
      >
        {title}
      </span>
    </div>
  );
}
