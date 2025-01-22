import type { RunLog } from '@cloud-pipeline/core';
import type { MappedLog } from '../types';

export const downloadFile = (blob: Blob, fileName: string) => {
  const blobUrl = URL.createObjectURL(blob);

  const link = document.createElement('a');
  link.href = blobUrl;
  link.download = fileName;

  document.body.appendChild(link);
  link.click();

  URL.revokeObjectURL(blobUrl);
  document.body.removeChild(link);
};

export default function downloadLog(
  logs: RunLog[] | MappedLog[],
  fileName = 'logs.txt',
) {
  const text = (logs || [])
    .filter((log) => log.logText?.length)
    .map((log) => log.logText)
    .join('\n');
  downloadFile(new Blob([text]), fileName);
}
