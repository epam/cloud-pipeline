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
        d="M 5,25 L 5,32 L 35,32 L 35,25 M 15,20 L 20,25 L 25,20 M 20,25 L 20,5"
        fill="none"
        strokeWidth="3"
      />
    </svg>
  );
}
