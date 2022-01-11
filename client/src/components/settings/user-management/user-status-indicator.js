import React from 'react';

export default function UserStatus ({online}) {
  return (
    <svg height="10" width="10">
      <circle
        cx="5" cy="5" r="5" stroke="none"
        className={online ? 'cp-user-status-online' : 'cp-user-status-offline'}
      />
    </svg>
  );
}
