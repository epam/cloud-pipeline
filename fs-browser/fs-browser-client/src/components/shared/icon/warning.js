import React from 'react';

export default function ({color, size}) {
  return (
    <svg
      viewBox="0 0 40 40"
      xmlns="http://www.w3.org/2000/svg"
      width={size || '100%'}
      height={size || '100%'}
      style={{stroke: color}}
    >
      <path
        d="M 20,8 L 5,33 L 35,33 Z M 20,17 L 20,27 M 20,28 L 20,30"
        strokeWidth="2"
        fill="none"
      />
    </svg>
  );
}
