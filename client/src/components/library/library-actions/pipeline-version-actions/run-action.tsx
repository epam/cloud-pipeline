import {useCallback} from 'react';
import {useNavigate} from 'react-router-dom';
import {useQuery} from '@tanstack/react-query';
import {Button, Dropdown, Space} from 'antd';
import {DownOutlined} from '@ant-design/icons';

import type {CommonProps} from '../../../../@types/common.ts';
import {pipelineQueryOptions, pipelineConfigurationsQueryOptions} from '../../../../queries';

type RunActionProps = CommonProps & {
  pipelineId?: number | string;
  version?: string;
};

function RunAction({pipelineId, version}: RunActionProps) {
  const numericId = pipelineId !== undefined ? Number(pipelineId) : undefined;
  const navigate = useNavigate();

  const {data: pipeline} = useQuery(pipelineQueryOptions(numericId));
  const {data: configurationsData = []} = useQuery(
    pipelineConfigurationsQueryOptions(numericId, version),
  );

  const executeAllowed = pipeline !== undefined && ((pipeline.mask ?? 0) & 4) === 4;

  const configurations = [...configurationsData].sort((a, b) => {
    if ((a.name ?? '') > (b.name ?? '')) return 1;
    if ((a.name ?? '') < (b.name ?? '')) return -1;
    return 0;
  });

  const onRun = useCallback(
    (configuration?: string) => {
      const baseUrl = `/launch/${pipelineId}/${version}`;
      if (configuration) {
        navigate(`${baseUrl}/${configuration}`);
        return;
      }
      const defaultConfig =
        configurations.find((c) => c.default) ??
        configurations.find((c) => /^default$/i.test(c.name ?? ''));
      navigate(`${baseUrl}/${defaultConfig?.name ?? 'default'}`);
    },
    [navigate, pipelineId, version, configurations],
  );

  if (!executeAllowed) return null;

  if (configurations.length > 1) {
    const configurationItems = configurations.map((c) => ({key: c.name ?? '', label: c.name}));
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
            onClick: ({key}) => onRun(key),
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
