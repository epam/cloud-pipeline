import React from 'react';

export default function Close(
  {
    className,
    onClick,
    bordered = true
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
          strokeWidth: 2,
          width: '100%',
          height: '100%'
        }}
      >
        <g>
          {
            bordered && (
              <circle
                cx="12"
                cy="12"
                r="12"
                stroke="transparent"
              />
            )
          }
          <circle
            cx="12"
            cy="12"
            r="10"
            stroke={bordered ? undefined : 'transparent'}
          />
          <path d="M8 8 L16 16 M8 16 L16 8"/>
        </g>
      </svg>
    </div>
  );
}
