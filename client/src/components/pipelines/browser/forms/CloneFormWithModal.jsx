import React, {useRef, useState} from 'react';
import PropTypes from 'prop-types';
import {Button, Modal, Row} from 'antd';
import roleModel from '../../../../utils/roleModel';
import pipelinesLibrary from '../../../../models/folders/FolderLoadTree';
import CloneForm from './CloneForm';

function CloneFormWithModal({parentId, visible, pending, onCancel, onSubmit}) {
  const formRef = useRef(null);
  const [selectedFolder, setSelectedFolder] = useState(null);

  const canWrite = selectedFolder
    ? roleModel.writeAllowed(selectedFolder)
    : roleModel.writeAllowed(pipelinesLibrary.value);

  const footer = pending ? (
    false
  ) : (
    <Row>
      <Button id="folder-clone-form-cancel-button" onClick={onCancel}>
        Cancel
      </Button>
      <Button
        id="folder-clone-form-ok-button"
        type="primary"
        disabled={!canWrite}
        onClick={() => formRef.current?.submit()}
      >
        Clone{selectedFolder ? ` into '${selectedFolder.name}'` : ' into Library'}
      </Button>
    </Row>
  );

  return (
    <Modal
      closable={!pending}
      open={visible}
      title="Select destination folder"
      width="50%"
      onCancel={onCancel}
      footer={footer}
    >
      <CloneForm
        parentId={parentId}
        visible={visible}
        pending={pending}
        onSubmit={onSubmit}
        onFolderChange={setSelectedFolder}
        formRef={formRef}
      />
    </Modal>
  );
}

CloneFormWithModal.propTypes = {
  parentId: PropTypes.number,
  visible: PropTypes.bool,
  pending: PropTypes.bool,
  onCancel: PropTypes.func,
  onSubmit: PropTypes.func,
};

export default CloneFormWithModal;
