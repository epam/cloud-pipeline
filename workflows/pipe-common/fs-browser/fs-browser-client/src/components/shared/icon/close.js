import React from 'react';

export default function ({color, size}) {
  return (
    <svg
      viewBox="0 0 40 40"
      xmlns="http://www.w3.org/2000/svg"
      width={size || '100%'}
      height={size || '100%'}
      style={{stroke: color}}
      shapeRendering="crispEdges"
    >
      <path
        d="M 8,8 L 32,32 M 32,8 L 8,32"
        strokeWidth="6"
        fill="none"
      />
    </svg>
  );
}
