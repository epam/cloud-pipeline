import {Button, message} from 'antd';
import {SettingOutlined} from '@ant-design/icons';

import type {CommonProps} from '../../../../@types/common.ts';

type SettingsActionProps = CommonProps & {
  configurationId?: number | string;
};

function SettingsAction(props: SettingsActionProps) {
  const {configurationId} = props;

  return (
    <Button
      id="edit-configuration-button"
      size="small"
      onClick={() => message.info(`[mock] Edit configuration ${configurationId}`)}
    >
      <SettingOutlined />
    </Button>
  );
}

export {SettingsAction};
