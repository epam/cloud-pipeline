import React, {useEffect, useState} from 'react';
import {ipcRenderer} from 'electron';
import {Button, Input} from 'antd';

function directoryNameDialog () {
  const [name, setName] = useState('');
  const onChange = e => {
    setName(e.target.value);
  }
  const onCancel = () => {
    ipcRenderer.send('directory-name-dialog', '');
  };
  const onCreate = () => {
    if (name) {
      ipcRenderer.send('directory-name-dialog', name);
    }
  }
  return (
    <div style={{padding: 10}}>
      <div style={{marginBottom: 10}}>
        <Input
          autoFocus
          value={name}
          onChange={onChange}
          placeholder="Directory name"
          onPressEnter={onCreate}
        />
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
          Cancel
        </Button>
        <Button
          disabled={!name}
          type="primary"
          onClick={onCreate}
        >
          Create
        </Button>
      </div>
    </div>
  );
}

export default directoryNameDialog;
