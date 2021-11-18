import React from 'react';

export const ArrowDirection = {
  left: 'left',
  right: 'right',
  top: 'top',
  bottom: 'bottom'
};

export default function Arrow(
  {
    className,
    direction = ArrowDirection.right,
    onClick,
    style
  }
) {
  let path = '';
  switch (direction) {
    case ArrowDirection.left:
      path = 'M16 4 L 8 12 L 16 20';
      break;
    case ArrowDirection.top:
      path = 'M4 16 L 12 8 L 20 16';
      break;
    case ArrowDirection.bottom:
      path = 'M4 8 L 12 16 L 20 8';
      break;
    case ArrowDirection.right:
    default:
      path = 'M8 4 L 16 12 L 8 20';
      break;
  }
  return (
    <svg
      className={className}
      enableBackground="new 0 0 91 91"
      height="24px"
      id="Layer_1"
      version="1.1"
      viewBox="0 0 24 24"
      width="24px"
      xmlns="http://www.w3.org/2000/svg"
      onClick={onClick}
      strokeWidth="4"
      style={
        Object.assign(
          {
            stroke: 'currentColor',
            fill: 'transparent',
            width: '1em',
            height: '1em'
          },
          style || {}
        )
      }
    >
      <g>
        <path d={path} />
      </g>
    </svg>
  );
}
