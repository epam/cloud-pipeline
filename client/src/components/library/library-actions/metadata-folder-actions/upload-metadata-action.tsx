import {Button, message} from 'antd';
import {UploadOutlined} from '@ant-design/icons';

import type {CommonProps} from '../../../../@types/common.ts';

type UploadMetadataActionProps = CommonProps & {
  folderId?: number | string;
  onUploaded?: () => void;
};

function UploadMetadataAction(props: UploadMetadataActionProps) {
  const {folderId, onUploaded} = props;

  return (
    <Button
      id="upload-metadata-folder-button"
      size="small"
      onClick={() => {
        message.info(`[mock] Upload metadata for folder ${folderId}`);
        onUploaded?.();
      }}
    >
      <UploadOutlined />
      Upload metadata
    </Button>
  );
}

export {UploadMetadataAction};
