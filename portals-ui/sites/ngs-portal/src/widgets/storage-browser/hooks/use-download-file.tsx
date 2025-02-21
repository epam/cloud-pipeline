import { downloadDataStorageFile } from '@cloud-pipeline/api';
import { message } from 'antd';
import { useCallback, useState } from 'react';

export const useDownloadFile = (storageId?: number) => {
  const [messageApi, contextHolder] = message.useMessage();
  const [isDownloading, setIsDownloading] = useState(false);

  const downloadFile = useCallback(
    async (name: string, path: string) => {
      if (!storageId) {
        return;
      }

      try {
        setIsDownloading(true);
        const { url } = await downloadDataStorageFile(storageId, path);

        if (url) {
          const link = document.createElement('a');
          link.href = url;
          link.download = name;
          document.body.appendChild(link);
          link.click();
          document.body.removeChild(link);
        }

        messageApi.open({
          key: 'download-file',
          type: 'success',
          content: (
            <span>
              Successfully downloaded <b>{name}</b>
            </span>
          ),
        });
      } catch {
        messageApi.open({
          key: 'download-file',
          type: 'error',
          content: (
            <span>
              Failed to download <b>{name}</b>
            </span>
          ),
        });
      } finally {
        setIsDownloading(false);
      }
    },
    [messageApi, storageId],
  );

  const handleDownload = useCallback(
    (name: string, path: string) => {
      void downloadFile(name, path);
    },
    [downloadFile],
  );

  return {
    handleDownload,
    isDownloading,
    downloadMessageContextHolder: contextHolder,
  };
};
