import React from 'react';

export default function Check(
  {
    className,
    onClick
  }
) {
  return (
    <div
      onClick={onClick}
      className={className}
      style={{
        width: '1em',
        height: '1em'
      }}
    >
      <svg
        viewBox="0 0 24 24"
        xmlns="http://www.w3.org/2000/svg"
        style={{
          stroke: 'currentColor',
          strokeWidth: 4,
          width: '100%',
          height: '100%'
        }}
      >
        <g>
          <path d="M6 12 L12 18 L22 2"/>
        </g>
      </svg>
    </div>
  );
}
