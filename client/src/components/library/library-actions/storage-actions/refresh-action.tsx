import {Button, message} from 'antd';
import {ReloadOutlined} from '@ant-design/icons';

import type {CommonProps} from '../../../../@types/common.ts';

type RefreshActionProps = CommonProps & {
  storageId?: number | string;
  pending?: boolean;
  onRefresh?: () => void;
};

function RefreshAction(props: RefreshActionProps) {
  const {storageId, pending = false, onRefresh} = props;

  return (
    <Button
      id="refresh-storage-button"
      size="small"
      disabled={pending}
      onClick={() => {
        message.info(`[mock] Refresh storage ${storageId}`);
        onRefresh?.();
      }}
    >
      <ReloadOutlined />
      Refresh
    </Button>
  );
}

export {RefreshAction};
