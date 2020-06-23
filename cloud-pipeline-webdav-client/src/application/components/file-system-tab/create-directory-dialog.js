import React, {useEffect, useState} from 'react';
import PropTypes from 'prop-types';
import {Button, Input, Modal} from 'antd';

function CreateDirectoryDialog({visible, onClose, onCreate}) {
  const [directoryName, setDirectoryName] = useState('');
  useEffect(() => {
    setDirectoryName('');
  }, [visible, setDirectoryName]);
  return (
    <Modal
      title="Create directory"
      visible={visible}
      onCancel={() => onClose && onClose()}
      footer={(
        <div
          style={{
            display: 'flex',
            flexDirection: 'row',
            alignItems: 'center',
            justifyContent: 'space-between'
          }}
        >
          <Button onClick={() => onClose && onClose()}>
            CANCEL
          </Button>
          <Button
            type="primary"
            disabled={!directoryName}
            onClick={() => onCreate && onCreate(directoryName)}
          >
            CREATE
          </Button>
        </div>
      )}
    >
      <Input
        value={directoryName}
        onChange={e => setDirectoryName(e.target.value)}
        placeholder="Directory name"
      />
    </Modal>
  );
}

CreateDirectoryDialog.propTypes = {
  visible: PropTypes.bool,
  onClose: PropTypes.func,
  onCreate: PropTypes.func,
};

export default CreateDirectoryDialog;
