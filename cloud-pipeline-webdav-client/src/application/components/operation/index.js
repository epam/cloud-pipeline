import React from 'react';
import {Progress} from 'antd';
import './operation.css';

const OPERATION_HEIGHT = 45;

function Operation ({operation}) {
  let content;
  if (!operation) {
    return null;
  }
  if (operation.error) {
    content = (
      <div className="error">
        {operation.error}
      </div>
    )
  } else if (!operation.progress) {
    content = (
      <div className="info">
        {operation.info}
      </div>
    )
  } else {
    content = (
      <div style={{width: '100%'}}>
        <div key="info" className="info">
          {operation.info}
        </div>
        <Progress
          key="progress"
          percent={operation.progress}
          size="small"
          format={o => `${Math.round(o)}%`}
        />
      </div>
    );
  }
  return (
    <div
      className="operation"
      style={{
        height: OPERATION_HEIGHT,
      }}
    >
      {content}
    </div>
  );
}

export default Operation;
