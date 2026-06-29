import {useRef} from 'react';
import {Button} from 'antd';
import {UploadOutlined} from '@ant-design/icons';

import type {CommonProps} from '../../../../@types/common.ts';
import {useMetadataActions} from './hooks.ts';
import MetadataEntityUpload from '../../../../models/folderMetadata/MetadataEntityUpload';
import UploadButton from '../../../special/UploadButton.jsx';

type UploadMetadataActionProps = CommonProps & {
  folderId?: number | string;
};

function UploadMetadataAction(props: UploadMetadataActionProps) {
  const {folderId} = props;
  const numericFolderId = folderId !== undefined ? Number(folderId) : undefined;

  const uploadButtonRef = useRef<{triggerClick: () => void} | null>(null);

  const {invalidateAfterMutation} = useMetadataActions(numericFolderId);

  return (
    <>
      <Button size="small" onClick={() => uploadButtonRef.current?.triggerClick()}>
        <UploadOutlined />
        Upload metadata
      </Button>
      {numericFolderId !== undefined && (
        <UploadButton
          multiple={false}
          synchronous
          style={{display: 'none'}}
          title="Upload metadata"
          action={MetadataEntityUpload.uploadUrl(numericFolderId)}
          onInitialized={(component: {triggerClick: () => void}) => {
            uploadButtonRef.current = component;
          }}
          onRefresh={invalidateAfterMutation}
        />
      )}
    </>
  );
}

export {UploadMetadataAction};
