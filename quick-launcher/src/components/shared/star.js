import React from 'react';

export default function Star({className, onClick, children}) {
  return (
    <div
      onClick={onClick}
      className={className}
    >
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'flex-end'
        }}
      >
        {children}
      </div>
      <svg
        xmlns="http://www.w3.org/2000/svg"
        x="0px"
        y="0px"
        width="24px"
        height="24px"
        viewBox="-50 -50 612 612"
        enableBackground="new -50 -50 612 612"
      >
        <polygon
          strokeWidth="40"
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeMiterlimit="10"
          points="259.216,29.942 330.27,173.919 489.16,197.007 374.185,309.08 401.33,467.31 259.216,392.612 117.104,467.31 144.25,309.08 29.274,197.007 188.165,173.919 "
        />
      </svg>
    </div>
  );
}
