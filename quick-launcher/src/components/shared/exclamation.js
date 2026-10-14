import React from 'react';

export default function Exclamation(
  {
    className,
    onClick,
    hint
  }
) {
  return (
    <div
      onClick={onClick}
      className={className}
      title={hint}
    >
      <svg
        viewBox="0 0 24 24"
        xmlns="http://www.w3.org/2000/svg"
        style={{
          stroke: 'currentColor',
          strokeWidth: 1,
          fill: 'transparent'
        }}
        x="0px"
        y="0px"
        width="24px"
        height="24px"
      >
        <g>
          <circle cx="12" cy="12" r="8" />
          <path d="M12,14 L12,6 M12,18 L12,16" />
        </g>
      </svg>
    </div>
  );
}
