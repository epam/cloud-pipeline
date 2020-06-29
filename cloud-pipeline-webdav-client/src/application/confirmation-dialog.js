import React, {useEffect, useState} from 'react';
import {ipcRenderer} from 'electron';
import {Button} from 'antd';

function ConfirmationDialog() {
  const [message, setMessage] = useState(undefined);
  useEffect(() => {
    const handle = (e, m) => setMessage(m);
    ipcRenderer.on('show-confirmation', handle);
    return () => {
      ipcRenderer.removeListener('show-confirmation', handle);
    };
  }, [message, setMessage]);
  if (!message) {
    return null;
  }
  const onCancel = () => {
    ipcRenderer.send('confirm', false);
  };
  const onSubmit = () => {
    ipcRenderer.send('confirm', true);
  }
  return (
    <div style={{padding: 10}}>
      <div style={{marginBottom: 10}}>
        {message}
      </div>
      <div
        style={{
          display: 'flex',
          flexDirection: 'row',
          justifyContent: 'space-between',
          alignItems: 'center'
        }}
      >
        <Button
          onClick={onCancel}
        >
          No
        </Button>
        <Button
          type="primary"
          onClick={onSubmit}
        >
          Yes
        </Button>
      </div>
    </div>
  );
}

export default ConfirmationDialog;
