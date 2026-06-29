import {Button, Dropdown} from 'antd';
import {EditOutlined, InboxOutlined, SettingOutlined} from '@ant-design/icons';
import {useCallback, useRef, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {useQuery} from '@tanstack/react-query';

import type {CommonProps} from '../../../../@types/common.ts';
import {dataStorageQueryOptions} from '../../../../queries';
import {StorageEditModal} from '../../../shared/object-actions/datastorage/edit/storage-edit-modal.tsx';

type SettingsActionProps = CommonProps & {
  storageId?: number | string;
  convertToVersionedStorageAvailable?: boolean;
};

function SettingsAction(props: SettingsActionProps) {
  const {storageId, convertToVersionedStorageAvailable = false} = props;
  const [open, setOpen] = useState(false);
  const [editVisible, setEditVisible] = useState(false);
  const navigate = useNavigate();

  const numericStorageId =
    storageId !== undefined ? Number(storageId) : undefined;

  const {data: storageData} = useQuery(
    dataStorageQueryOptions(numericStorageId, {
      enabled: editVisible && numericStorageId !== undefined,
    }),
  );

  // Capture parentFolderId before deletion so it survives cache invalidation
  const parentFolderIdRef = useRef<number | undefined>(undefined);
  const resolvedParentId =
    storageData?.parentFolderId ?? storageData?.parent?.id;
  if (resolvedParentId !== undefined) {
    parentFolderIdRef.current = resolvedParentId;
  }

  const handleDeleted = useCallback(() => {
    setEditVisible(false);
    const parentId = parentFolderIdRef.current;
    if (parentId !== undefined) {
      navigate(`/folder/${parentId}`);
    } else {
      navigate('/library');
    }
  }, [navigate]);

  const onClick = useCallback(
    ({key}: {key: string}) => {
      setOpen(false);
      switch (key) {
        case 'edit':
          setEditVisible(true);
          break;
        case 'convert':
          // TODO: wire convert to versioned storage
          break;
        default:
          break;
      }
    },
    [],
  );

  return (
    <>
      {convertToVersionedStorageAvailable ? (
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
      ) : (
        <Button
          id="edit-storage-button"
          size="small"
          onClick={() => setEditVisible(true)}
        >
          <SettingOutlined />
        </Button>
      )}
      {numericStorageId !== undefined && (
        <StorageEditModal
          storageId={numericStorageId}
          open={editVisible}
          onClose={() => setEditVisible(false)}
          onDeleted={handleDeleted}
        />
      )}
    </>
  );
}

export {SettingsAction};
