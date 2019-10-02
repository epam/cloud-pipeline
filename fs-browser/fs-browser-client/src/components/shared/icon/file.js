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
      <defs>
        <clipPath id="cut-off-bottom">
          <rect x="8" y="3" width="24" height="34" />
        </clipPath>
      </defs>
      <path
        d="M 20 5 10 5 10 35 30 35 30 15 20 5 20 15 30 15"
        fill="none"
        strokeWidth="2"
        clipPath="url(#cut-off-bottom)"
      />
    </svg>
  );
}
