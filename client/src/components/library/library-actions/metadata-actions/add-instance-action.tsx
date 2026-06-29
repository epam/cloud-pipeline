import {useCallback, useState} from 'react';
import {Button, message} from 'antd';
import {PlusOutlined} from '@ant-design/icons';

import type {CommonProps} from '../../../../@types/common.ts';
import {useMetadataActions} from './hooks.ts';
import AddInstanceForm from '../../../pipelines/browser/forms/AddInstanceForm.jsx';

type AddInstanceActionProps = CommonProps & {
  folderId?: number | string;
  metadataClass?: string;
};

function AddInstanceAction(props: AddInstanceActionProps) {
  const {folderId, metadataClass} = props;
  const numericFolderId = folderId !== undefined ? Number(folderId) : undefined;

  const [visible, setVisible] = useState(false);
  const [pending, setPending] = useState(false);

  const {entityTypes, currentMetadataClassId, addInstance} = useMetadataActions(
    numericFolderId,
    metadataClass,
  );

  const handleCreate = useCallback(
    async (values: Record<string, unknown>) => {
      setPending(true);
      try {
        await addInstance(values);
        setVisible(false);
      } catch (error) {
        message.error(String(error), 5);
      } finally {
        setPending(false);
      }
    },
    [addInstance],
  );

  return (
    <>
      <Button size="small" onClick={() => setVisible(true)}>
        <PlusOutlined />
        Add instance
      </Button>
      <AddInstanceForm
        folderId={numericFolderId}
        visible={visible}
        pending={pending}
        onCreate={handleCreate}
        onCancel={() => setVisible(false)}
        entityType={currentMetadataClassId}
        entityTypes={entityTypes}
      />
    </>
  );
}

export {AddInstanceAction};
