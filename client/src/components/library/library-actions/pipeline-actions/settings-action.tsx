import {Button, Dropdown, message} from 'antd';
import {CopyOutlined, EditOutlined, SettingOutlined} from '@ant-design/icons';
import {useCallback, useState} from 'react';

import type {CommonProps} from '../../../../@types/common.ts';

type SettingsActionProps = CommonProps & {
  pipelineId?: number | string;
  isOwner?: boolean;
  readOnly?: boolean;
};

function SettingsAction(props: SettingsActionProps) {
  const {pipelineId, isOwner = false, readOnly = false} = props;
  const [open, setOpen] = useState(false);

  const onClick = useCallback(
    ({key}: {key: string}) => {
      setOpen(false);
      switch (key) {
        case 'edit':
          message.info(`[mock] Edit pipeline ${pipelineId}`);
          break;
        case 'clone':
          message.info(`[mock] Clone pipeline ${pipelineId}`);
          break;
        default:
          break;
      }
    },
    [pipelineId],
  );

  if (readOnly) {
    return null;
  }

  const items = [
    {
      key: 'edit',
      id: 'edit-pipeline-button',
      label: (
        <span>
          <EditOutlined /> Edit
        </span>
      ),
    },
    ...(isOwner
      ? [
          {
            key: 'clone',
            id: 'clone-pipeline-button',
            label: (
              <span>
                <CopyOutlined /> Clone
              </span>
            ),
          },
        ]
      : []),
  ];

  if (items.length === 0) {
    return null;
  }

  return (
    <Dropdown
      placement="bottomRight"
      trigger={['click']}
      open={open}
      onOpenChange={setOpen}
      menu={{items, onClick, style: {width: 100}}}
    >
      <Button key="edit" id="edit-pipeline-menu-button" size="small">
        <SettingOutlined />
      </Button>
    </Dropdown>
  );
}

export {SettingsAction};
