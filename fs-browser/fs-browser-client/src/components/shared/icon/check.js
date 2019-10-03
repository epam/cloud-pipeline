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
        d="M 8,20 L 20,30 L 32,8"
        strokeWidth="6"
        fill="none"
      />
    </svg>
  );
}
