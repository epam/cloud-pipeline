import {Button, message} from 'antd';
import {SettingOutlined} from '@ant-design/icons';

import type {CommonProps} from '../../../../@types/common.ts';

type SettingsActionProps = CommonProps & {
  storageId?: number | string;
  readOnly?: boolean;
};

function SettingsAction(props: SettingsActionProps) {
  const {storageId, readOnly = false} = props;

  return (
    <Button
      size="small"
      disabled={readOnly}
      onClick={() => message.info(`[mock] Edit versioned storage ${storageId}`)}
    >
      <SettingOutlined />
    </Button>
  );
}

export {SettingsAction};
