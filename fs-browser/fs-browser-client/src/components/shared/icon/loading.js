import React from 'react';
import styles from './icons.css';

export default function ({color, size, width}) {
  return (
    <svg
      className={styles.rotating}
      viewBox="0 0 40 40"
      xmlns="http://www.w3.org/2000/svg"
      width={size || '100%'}
      height={size || '100%'}
      style={{stroke: color}}
    >
      <path
        d="M 35 20 A 15 15 0 1 0 20 35"
        strokeWidth={width || 2}
        fill="none"
      />
    </svg>
  );
}
