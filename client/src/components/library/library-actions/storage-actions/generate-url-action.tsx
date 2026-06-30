import {useState} from 'react';
import {useSearchParams} from 'react-router-dom';
import {Alert, Button, Checkbox, Input, message, Modal, Spin} from 'antd';
import {useQuery} from '@tanstack/react-query';

import type {CommonProps} from '../../../../@types/common.ts';
import {dataStorageQueryOptions} from '../../../../queries';
import {useCloudRegion} from '../../../../queries/cloud-regions/hooks.ts';
import {useIsAdministrator} from '../../../../stores/users/hooks.ts';
import {useStringPreferenceValue} from '../../../../queries/preferences/hooks.ts';
import {generateDataStorageDownloadUrls} from '../../../../api/datastorage/datastorage-api.ts';
import roleModel from '../../../../utils/roleModel.jsx';

type GenerateUrlActionProps = CommonProps & {
  storageId?: number | string;
};

function GenerateUrlAction(props: GenerateUrlActionProps) {
  const {storageId} = props;
  const [searchParams] = useSearchParams();
  const [open, setOpen] = useState(false);
  const [writeAccess, setWriteAccess] = useState(false);
  const [url, setUrl] = useState<string | undefined>(undefined);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);

  const numericId = storageId !== undefined ? Number(storageId) : undefined;
  const {data: storage} = useQuery(dataStorageQueryOptions(numericId, {enabled: numericId !== undefined}));

  const region = useCloudRegion(storage?.regionId);
  const isAdmin = useIsAdministrator();
  const signedUrlsPref = useStringPreferenceValue('storage.allow.signed.urls');

  const isAzure = /^azure$/i.test(region?.provider ?? '');
  const signedUrlsAllowed = isAdmin || `${signedUrlsPref}` !== 'false';
  const available = isAzure && signedUrlsAllowed;

  const writeAllowed = storage ? roleModel.writeAllowed(storage) : false;

  const generateUrl = async (withWrite: boolean) => {
    if (numericId === undefined) return;
    setPending(true);
    setError(undefined);
    setUrl(undefined);
    try {
      let path = decodeURIComponent(searchParams.get('path') ?? '');
      if (path.length > 0 && !path.endsWith('/')) {
        path += '/';
      }
      const permissions = ['READ', withWrite ? 'WRITE' : undefined].filter(Boolean) as string[];
      const result = await generateDataStorageDownloadUrls(numericId, {paths: [path], permissions});
      setUrl(result.map((u) => u.url).join('\n'));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Error generating URL');
    } finally {
      setPending(false);
    }
  };

  const handleOpen = () => {
    setOpen(true);
    setWriteAccess(false);
    generateUrl(false);
  };

  const handleClose = () => {
    setOpen(false);
    setUrl(undefined);
    setError(undefined);
  };

  const handleWriteAccessChange = (checked: boolean) => {
    setWriteAccess(checked);
    generateUrl(checked);
  };

  if (!available) return null;

  return (
    <>
      <Button id="generate-folder-url" size="small" onClick={handleOpen}>
        Generate URL
      </Button>
      <Modal
        title="Download file url"
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
        {pending && (
          <div style={{textAlign: 'center', padding: '16px 0'}}>
            <Spin />
          </div>
        )}
        {!pending && url && (
          <Input.TextArea
            autoSize
            value={url}
            readOnly
          />
        )}
        {!pending && error && <Alert type="error" message={error} />}
        <div style={{marginTop: 10}}>
          <Checkbox
            checked={writeAccess}
            disabled={!writeAllowed || pending}
            onChange={(e) => handleWriteAccessChange(e.target.checked)}
          >
            Write access
          </Checkbox>
        </div>
      </Modal>
    </>
  );
}

export {GenerateUrlAction};
