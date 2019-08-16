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
        d="M 5,10 L 5,30 L 35,30 L 35,15 L 15,15 L 15,10 Z"
        fill="none"
        strokeWidth="2"
      />
    </svg>
  );
}
