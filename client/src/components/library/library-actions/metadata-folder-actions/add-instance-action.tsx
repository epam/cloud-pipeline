import {Button, message} from 'antd';
import {PlusOutlined} from '@ant-design/icons';

import type {CommonProps} from '../../../../@types/common.ts';

type AddInstanceActionProps = CommonProps & {
  folderId?: number | string;
  disabled?: boolean;
};

function AddInstanceAction(props: AddInstanceActionProps) {
  const {folderId, disabled = false} = props;

  return (
    <Button
      id="add-metadata-button"
      size="small"
      disabled={disabled}
      onClick={() => message.info(`[mock] Add metadata instance in folder ${folderId}`)}
    >
      <PlusOutlined />
      Add instance
    </Button>
  );
}

export {AddInstanceAction};
