import { downloadDataStorageFile } from '@cloud-pipeline/api';
import { message } from 'antd';
import { useCallback, useMemo, useState } from 'react';
import type { DataStorageItem } from '@cloud-pipeline/core';

export const useDownloadFile = (storageId?: number) => {
  const [messageApi, contextHolder] = message.useMessage();
  const [isDownloading, setIsDownloading] = useState(false);

  const downloadFile = useCallback(
    async (item: DataStorageItem) => {
      if (!storageId) {
        return;
      }

      try {
        setIsDownloading(true);
        const { url } = await downloadDataStorageFile(storageId, item.path);

        if (url) {
          const link = document.createElement('a');
          link.href = url;
          link.download = item.name;
          document.body.appendChild(link);
          link.click();
          document.body.removeChild(link);
        }

        messageApi.open({
          key: 'download-file',
          type: 'success',
          content: (
            <span>
              Successfully downloaded <b>{item.name}</b>
            </span>
          ),
        });
      } catch {
        messageApi.open({
          key: 'download-file',
          type: 'error',
          content: (
            <span>
              Failed to download <b>{item.name}</b>
            </span>
          ),
        });
      } finally {
        setIsDownloading(false);
      }
    },
    [messageApi, storageId],
  );

  const onDownloadItem = useCallback(
    (item: DataStorageItem) => {
      void downloadFile(item);
    },
    [downloadFile],
  );

  return useMemo(
    () => ({
      onDownloadItem,
      isDownloading,
      downloadMessageContextHolder: contextHolder,
    }),
    [onDownloadItem, isDownloading, contextHolder],
  );
};
