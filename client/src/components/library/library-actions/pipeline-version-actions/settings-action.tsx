import {Button, message} from 'antd';
import {SettingOutlined} from '@ant-design/icons';

import type {CommonProps} from '../../../../@types/common.ts';

type SettingsActionProps = CommonProps & {
  pipelineId?: number | string;
  version?: string;
};

function SettingsAction(props: SettingsActionProps) {
  const {pipelineId, version} = props;

  const onClick = () => {
    message.info(`[mock] Edit pipeline ${pipelineId} (${version})`);
  };

  return (
    <Button id="edit-pipeline-button" size="small" style={{lineHeight: 1}} onClick={onClick}>
      <SettingOutlined />
    </Button>
  );
}

export {SettingsAction};
