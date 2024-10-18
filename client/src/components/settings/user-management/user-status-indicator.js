import React from 'react';

export default function UserStatus ({online}) {
  return (
    <svg height="10" width="10">
      <circle
        cx="5"
        cy="5"
        r="4"
        strokeWidth={1}
        className={online ? 'cp-status-online' : 'cp-status-offline'}
      />
    </svg>
  );
}
