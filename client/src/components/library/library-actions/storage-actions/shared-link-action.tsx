import {Button, message} from 'antd';
import {LinkOutlined} from '@ant-design/icons';

import type {CommonProps} from '../../../../@types/common.ts';

type SharedLinkActionProps = CommonProps & {
  storageId?: number | string;
};

function SharedLinkAction(props: SharedLinkActionProps) {
  const {storageId} = props;

  return (
    <Button
      id="storage-shared-link-button"
      size="small"
      onClick={() => message.info(`[mock] Shared link for storage ${storageId}`)}
    >
      <LinkOutlined />
    </Button>
  );
}

export {SharedLinkAction};
