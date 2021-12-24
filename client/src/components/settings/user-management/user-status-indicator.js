import React from 'react';

export default function UserStatus ({online}) {
  return (
    <svg height="10" width="10">
      <circle cx="5" cy="5" r="5" stroke="none" fill={online ? 'lightgreen' : 'grey'} />
    </svg>
  );
}
