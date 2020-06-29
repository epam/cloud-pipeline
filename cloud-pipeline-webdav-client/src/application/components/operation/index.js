import React from 'react';
import {Progress} from 'antd';
import './operation.css';

const OPERATION_HEIGHT = 45;

function Operation ({operation}) {
  if (!operation) {
    return null;
  }
  const parts = [];
  if (operation.info) {
    parts.push((
      <div key="info" className="info">
        {operation.info}
      </div>
    ));
  }
  if (operation.error) {
    parts.push((
      <div key="error" className="error">
        {operation.error}
      </div>
    ))
  } else if (operation.progress) {
    parts.push((
      <Progress
        key="progress"
        percent={operation.progress}
        size="small"
        format={o => `${Math.round(o)}%`}
      />
    ));
  }
  return (
    <div
      className="operation"
      style={{
        height: OPERATION_HEIGHT,
      }}
    >
      {parts}
    </div>
  );
}

export default Operation;
