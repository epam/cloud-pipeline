import React, {useEffect, useState} from 'react';
import {remote} from 'electron';
import Operation from './components/operation';

const UPDATE_INTERVAL = 100;

function Operations() {
  const [refreshIdx, setRefreshIdx] = useState(0);
  const [operations, setOperations] = useState([]);
  useEffect(() => {
    const t = setTimeout(() => {
      setRefreshIdx(refreshIdx + 1);
    }, UPDATE_INTERVAL);
    return () => {
      clearTimeout(t);
    }
  }, [refreshIdx]);
  useEffect(() => {
    setOperations(
      (remote.getGlobal('operations') || [])
        .filter(o => !o.finished)
    );
  }, [refreshIdx]);
  return (
    <div>
      {
        operations.map((operation) => (
          <Operation key={operation.identifier} operation={operation} />
        ))
      }
    </div>
  );
}

export default Operations;
