import {Button, message} from 'antd';
import {DeleteOutlined} from '@ant-design/icons';

import type {CommonProps} from '../../../../@types/common.ts';

type DeleteMetadataActionProps = CommonProps & {
  folderId?: number | string;
};

function DeleteMetadataAction(props: DeleteMetadataActionProps) {
  const {folderId} = props;

  return (
    <Button
      id="delete-metadata-button"
      size="small"
      danger
      onClick={() => message.info(`[mock] Delete metadata in folder ${folderId}`)}
    >
      <DeleteOutlined />
      Delete metadata
    </Button>
  );
}

export {DeleteMetadataAction};
