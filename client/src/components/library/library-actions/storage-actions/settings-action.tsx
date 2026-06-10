import {Button, Dropdown, message} from 'antd';
import {EditOutlined, InboxOutlined, SettingOutlined} from '@ant-design/icons';
import {useCallback, useState} from 'react';

import type {CommonProps} from '../../../../@types/common.ts';

type SettingsActionProps = CommonProps & {
  storageId?: number | string;
  convertToVersionedStorageAvailable?: boolean;
};

function SettingsAction(props: SettingsActionProps) {
  const {storageId, convertToVersionedStorageAvailable = false} = props;
  const [open, setOpen] = useState(false);

  const onClick = useCallback(
    ({key}: {key: string}) => {
      setOpen(false);
      switch (key) {
        case 'edit':
          message.info(`[mock] Edit storage ${storageId}`);
          break;
        case 'convert':
          message.info(`[mock] Convert storage ${storageId} to versioned storage`);
          break;
        default:
          break;
      }
    },
    [storageId],
  );

  if (convertToVersionedStorageAvailable) {
    return (
      <Dropdown
        placement="bottomRight"
        trigger={['click']}
        open={open}
        onOpenChange={setOpen}
        menu={{
          items: [
            {
              key: 'edit',
              label: (
                <span>
                  <EditOutlined /> Edit
                </span>
              ),
            },
            {
              key: 'convert',
              label: (
                <span>
                  <InboxOutlined className="cp-versioned-storage" /> Convert to Versioned Storage
                </span>
              ),
            },
          ],
          onClick,
          style: {width: 200},
        }}
      >
        <Button id="edit-storage-button" size="small">
          <SettingOutlined />
        </Button>
      </Dropdown>
    );
  }

  return (
    <Button
      id="edit-storage-button"
      size="small"
      onClick={() => message.info(`[mock] Edit storage ${storageId}`)}
    >
      <SettingOutlined />
    </Button>
  );
}

export {SettingsAction};
