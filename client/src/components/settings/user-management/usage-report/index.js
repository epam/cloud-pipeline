import React from 'react';
import Filters from './filters';

export default function UsageReport ({children, location, router}) {
  return (
    <Filters location={location} router={router}>
      {children}
    </Filters>);
}
