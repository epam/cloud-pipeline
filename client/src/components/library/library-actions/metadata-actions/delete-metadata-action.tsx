import {useCallback} from 'react';
import {Button, message, Modal} from 'antd';
import {DeleteOutlined} from '@ant-design/icons';

import type {CommonProps} from '../../../../@types/common.ts';
import {useMetadataActions} from './hooks.ts';

type DeleteMetadataActionProps = CommonProps & {
  folderId?: number | string;
  metadataClass?: string;
};

function DeleteMetadataAction(props: DeleteMetadataActionProps) {
  const {folderId, metadataClass} = props;
  const numericFolderId = folderId !== undefined ? Number(folderId) : undefined;

  const {deleteClass} = useMetadataActions(numericFolderId, metadataClass);

  const handleClick = useCallback(() => {
    if (!metadataClass || numericFolderId === undefined) return;
    Modal.confirm({
      title: `Delete class '${metadataClass}'?`,
      onOk: async () => {
        const hide = message.loading(`Removing class '${metadataClass}'...`, 0);
        try {
          await deleteClass();
        } catch (error) {
          message.error(String(error), 5);
        } finally {
          hide();
        }
      },
    });
  }, [metadataClass, numericFolderId, deleteClass]);

  return (
    <Button danger size="small" onClick={handleClick}>
      <DeleteOutlined />
      Delete class
    </Button>
  );
}

export {DeleteMetadataAction};
