import {useState} from 'react';
import {Alert, Button, Input, Modal} from 'antd';
import {LinkOutlined} from '@ant-design/icons';
import {useQuery} from '@tanstack/react-query';

import type {CommonProps} from '../../../../@types/common.ts';
import {dataStorageQueryOptions} from '../../../../queries';
import {useStringPreferenceValue} from '../../../../queries/preferences/hooks.ts';
import {generateDataStorageSharedLink} from '../../../../api/datastorage/datastorage-api.ts';
import roleModel from '../../../../utils/roleModel.jsx';
import LoadingView from '../../../special/LoadingView.tsx';
import BashCode from '../../../special/bash-code';
import styles from './shared-link-action.module.css';

type SharedLinkActionProps = CommonProps & {
  storageId?: number | string;
};

function SharedLinkAction(props: SharedLinkActionProps) {
  const {storageId} = props;
  const [open, setOpen] = useState(false);
  const [link, setLink] = useState<string | undefined>(undefined);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);

  const numericId = storageId !== undefined ? Number(storageId) : undefined;
  const {data: storage} = useQuery(dataStorageQueryOptions(numericId, {enabled: numericId !== undefined}));

  const disclaimer = useStringPreferenceValue('data.sharing.disclaimer');

  const isSharedAvailable =
    storage !== undefined &&
    roleModel.writeAllowed(storage) &&
    !/^nfs$/i.test(storage.type ?? '') &&
    storage.shared;

  const fetchLink = async () => {
    if (numericId === undefined) return;
    setPending(true);
    setError(undefined);
    setLink(undefined);
    try {
      const result = await generateDataStorageSharedLink(numericId);
      setLink(result);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Error fetching shared link');
    } finally {
      setPending(false);
    }
  };

  const handleOpen = () => {
    setOpen(true);
    fetchLink();
  };

  const handleClose = () => {
    setOpen(false);
    setLink(undefined);
    setError(undefined);
  };

  if (!isSharedAvailable) return null;

  const formattedDisclaimer = disclaimer
    ?.replace(/\\n/g, '\n')
    .replace(/\\r/g, '\r');

  return (
    <>
      <Button id="storage-shared-link-button" size="small" onClick={handleOpen}>
        <LinkOutlined />
      </Button>
      <Modal
        title="Share storage link"
        width="80%"
        open={open}
        onOk={handleClose}
        onCancel={handleClose}
        footer={
          <Button type="primary" onClick={handleClose}>
            OK
          </Button>
        }
      >
        <div>
          {pending && <LoadingView />}
          {!pending && link && <Input.TextArea autoSize value={link} />}
          {!pending && error && <Alert message={error} type="error" />}
          {formattedDisclaimer && (
            <BashCode
              id="data-sharing-disclaimer"
              className={styles.dataSharingDisclaimer}
              code={formattedDisclaimer}
              loading={false}
              style={undefined}
              breakLines={false}
              nowrap={false}
            />
          )}
        </div>
      </Modal>
    </>
  );
}

export {SharedLinkAction};
