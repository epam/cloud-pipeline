import {Button, Dropdown, Space, message} from 'antd';
import {DownOutlined} from '@ant-design/icons';
import {useCallback} from 'react';

import type {CommonProps} from '../../../../@types/common.ts';

type RunActionProps = CommonProps & {
  pipelineId?: number | string;
  version?: string;
  configurations?: string[];
  executable?: boolean;
};

function RunAction(props: RunActionProps) {
  const {pipelineId, version, configurations = [], executable = true} = props;

  const onRun = useCallback(
    (configuration?: string) => {
      const cfg = configuration ? `/${configuration}` : '';
      message.info(`[mock] Run pipeline ${pipelineId}/${version ?? 'latest'}${cfg}`);
    },
    [pipelineId, version],
  );

  const onSelectConfiguration = useCallback(
    ({key}: {key: string}) => {
      onRun(key);
    },
    [onRun],
  );

  if (!executable) {
    return null;
  }

  if (configurations.length > 1) {
    const configurationItems = configurations.map((name) => ({key: name, label: name}));
    return (
      <Space.Compact style={{display: 'inline-flex'}}>
        <Button
          id="launch-pipeline-button"
          size="small"
          type="primary"
          style={{lineHeight: 1}}
          onClick={() => onRun()}
        >
          RUN
        </Button>
        <Dropdown
          menu={{
            items: configurationItems,
            onClick: onSelectConfiguration,
            style: {cursor: 'pointer'},
          }}
          placement="bottomRight"
          trigger={['click']}
        >
          <Button size="small" id="run-dropdown-button" type="primary">
            <DownOutlined style={{lineHeight: 'inherit', verticalAlign: 'middle'}} />
          </Button>
        </Dropdown>
      </Space.Compact>
    );
  }

  return (
    <Button
      id="launch-pipeline-button"
      size="small"
      type="primary"
      style={{lineHeight: 1}}
      onClick={() => onRun()}
    >
      RUN
    </Button>
  );
}

export {RunAction};
