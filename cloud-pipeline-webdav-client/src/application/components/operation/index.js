import React from 'react';
import {Progress} from 'antd';
import {CloseCircleFilled} from '@ant-design/icons';
import './operation.css';

const OPERATION_HEIGHT = 45;

function Operation ({operation, style}) {
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
        className="progress"
        percent={operation.progress}
        size="small"
        format={o => `${Math.round(o)}%`}
      />
    ));
  }
  return (
    <div className="operation-container">
      <div
        className="operation"
        style={Object.assign({
          height: OPERATION_HEIGHT,
        }, style)}
      >
        {parts}
      </div>
      <CloseCircleFilled
        className="close"
        onClick={() => operation.abort()}
      />
    </div>
  );
}

export default Operation;
export {OPERATION_HEIGHT};
