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
