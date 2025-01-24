import { Button, Spin } from 'antd';
import { usePipelineFiles } from '../hooks';
import {
  DocumentTextIcon,
  ArrowDownTrayIcon,
} from '@heroicons/react/24/outline';
import { PageSpinner } from '../../../shared/ui';
import { fetchPipelineFileByPath } from '@cloud-pipeline/api';
import { useCallback, useEffect, useState } from 'react';
import { Markdown } from '@cloud-pipeline/components';
import cn from 'classnames';
import { decodeBase64, downloadFile } from '../../../shared/helpers';

type Props = {
  pipelineId: number;
  version: string;
};

const allowedFormats = ['md', 'txt', 'csv', 'json', 'config'];

export const PipelineFiles = ({ pipelineId, version }: Props) => {
  const [fileName, setFileName] = useState<string | null>(null);
  const [fileContent, setFileContent] = useState<string | null>(null);
  const [isFileContentLoading, setIsFileContentLoading] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);

  const {
    error: pipelineFilesError,
    isLoading: isPipelineFilesLoading,
    pipelineFiles,
  } = usePipelineFiles(pipelineId, version);

  const handleDownload = useCallback(
    async (path: string, fileName: string) => {
      const encodedPath = encodeURIComponent(path);
      setIsDownloading(true);

      try {
        const base64String = await fetchPipelineFileByPath(
          pipelineId,
          version,
          encodedPath,
        );
        const decoded = decodeBase64(base64String);

        const blobType = fileName.endsWith('.docx')
          ? 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
          : 'text/plain';
        const blob = new Blob([decoded], { type: blobType });
        downloadFile(blob, fileName);
      } catch (err) {
        console.log('Failed to download file', err);
      } finally {
        setIsDownloading(false);
      }
    },
    [pipelineId, version],
  );

  const handleSelect = useCallback(
    async (path: string, fileName: string) => {
      setFileName(fileName);

      const fileExtension = fileName.split('.').pop();

      if (!fileExtension || !allowedFormats.includes(fileExtension)) {
        setFileContent('Preview cannot be displayed');
        return;
      }

      const encodedPath = encodeURIComponent(path);
      setIsFileContentLoading(true);

      try {
        const base64String = await fetchPipelineFileByPath(
          pipelineId,
          version,
          encodedPath,
        );

        setFileContent(decodeBase64(base64String));
      } catch (err) {
        console.error('Failed to fetch file content:', err);
        setFileContent('Error loading file content');
      } finally {
        setIsFileContentLoading(false);
      }
    },
    [pipelineId, version],
  );

  useEffect(() => {
    const readmeFile = pipelineFiles?.find((file) => file.name === 'README.md');

    if (readmeFile) {
      void handleSelect(readmeFile.path, readmeFile.name);
    } else {
      setFileContent(null);
      setFileName(null);
    }
  }, [handleSelect, pipelineFiles]);

  const renderFileContent = () => {
    if (isFileContentLoading) {
      return <PageSpinner />;
    }

    return (
      <div className="py-2">
        {fileContent && fileName?.endsWith('.md') ? (
          <Markdown>{fileContent}</Markdown>
        ) : (
          <pre className="whitespace-pre-wrap">{fileContent}</pre>
        )}
      </div>
    );
  };

  if (pipelineFilesError) {
    return <div>Error fetching files</div>;
  }

  if (isPipelineFilesLoading) {
    return <PageSpinner />;
  }

  const noFilesFound = !pipelineFiles || pipelineFiles?.length === 0;

  return (
    <div className="h-full flex flex-col">
      <div className="border-b-2 mx-[-16px]">
        {noFilesFound ? (
          <div className="px-4 pb-4">No files found</div>
        ) : (
          pipelineFiles?.map(({ name, path }) => (
            <div
              onClick={() => void handleSelect(path, name)}
              className={cn('flex items-center px-4 py-2 cursor-pointer', {
                'hover:text-sky-500': fileName !== name,
                'text-sky-500': fileName === name,
              })}
              key={name}>
              <DocumentTextIcon className="w-5 h-5" />
              <p className="font-bold ml-2">{name}</p>
              <Button
                className="ml-auto w-[30px] h-[30px] p-0"
                disabled={isDownloading}
                onClick={(e) => {
                  e.stopPropagation();
                  void handleDownload(path, name);
                }}>
                {isDownloading ? (
                  <Spin size="small" />
                ) : (
                  <ArrowDownTrayIcon className="w-5 h-5" />
                )}
              </Button>
            </div>
          ))
        )}
      </div>

      <div className="flex-grow">{renderFileContent()}</div>
    </div>
  );
};
