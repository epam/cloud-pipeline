import {Button, Dropdown, message} from 'antd';
import {
  CloudUploadOutlined,
  DeleteOutlined,
  PlusOutlined,
  SettingOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import {useCallback, useState} from 'react';

import type {CommonProps} from '../../../../@types/common.ts';

type SettingsActionProps = CommonProps & {
  folderId?: number | string;
  metadataClass?: string;
  attributesVisible?: boolean;
  transferAvailable?: boolean;
  onToggleAttributes?: (visible: boolean) => void;
};

function SettingsAction(props: SettingsActionProps) {
  const {
    folderId,
    metadataClass,
    attributesVisible = false,
    transferAvailable = false,
    onToggleAttributes,
  } = props;
  const [open, setOpen] = useState(false);

  const onClick = useCallback(
    ({key}: {key: string}) => {
      setOpen(false);
      switch (key) {
        case 'add-metadata':
          message.info(`[mock] Add metadata instance in ${metadataClass} (folder ${folderId})`);
          break;
        case 'upload':
          message.info(`[mock] Upload metadata for ${metadataClass} (folder ${folderId})`);
          break;
        case 'transfer':
          message.info(`[mock] Transfer ${metadataClass} to the cloud`);
          break;
        case 'delete':
          message.info(`[mock] Delete metadata class ${metadataClass}`);
          break;
        case 'show-attributes':
          onToggleAttributes?.(!attributesVisible);
          break;
        default:
          break;
      }
    },
    [attributesVisible, folderId, metadataClass, onToggleAttributes],
  );

  const items = [
    {
      key: 'add-metadata',
      label: (
        <span>
          <PlusOutlined style={{marginRight: 5}} />
          Add instance
        </span>
      ),
    },
    {
      key: 'upload',
      label: (
        <span>
          <UploadOutlined style={{marginRight: 5}} />
          Upload metadata
        </span>
      ),
    },
    ...(transferAvailable
      ? [
          {
            key: 'transfer',
            label: (
              <span>
                <CloudUploadOutlined style={{marginRight: 5}} />
                Transfer to the cloud
              </span>
            ),
          },
          {type: 'divider' as const, key: 'divider-1'},
        ]
      : []),
    {
      key: 'delete',
      label: (
        <span className="cp-danger">
          <DeleteOutlined style={{marginRight: 5}} />
          Delete class
        </span>
      ),
    },
    {type: 'divider' as const, key: 'divider-2'},
    {
      key: 'show-attributes',
      label: attributesVisible ? 'Hide attributes' : 'Show attributes',
    },
  ];

  return (
    <Dropdown trigger={['click']} open={open} onOpenChange={setOpen} menu={{items, onClick}}>
      <Button id="metadata-actions-button" size="small">
        <SettingOutlined />
      </Button>
    </Dropdown>
  );
}

export {SettingsAction};
