import { useState } from 'react';
import { StoragePath } from './storage-path';

const storageOptions = [
  { label: 's3://aln-spatial-data-dev', value: 's3://aln-spatial-data-dev' },
  { label: 's3://another-storage', value: 's3://another-storage' },
  { label: 'gcs://example-bucket', value: 'gcs://example-bucket' },
];

export const ProjectStorage = () => {
  const [path, setPath] = useState('');
  const [selectedStorage, setSelectedStorage] = useState(
    storageOptions[0].value,
  );

  return (
    <StoragePath
      storageOptions={storageOptions}
      selectedStorage={selectedStorage}
      onStorageChange={setSelectedStorage}
      path={path}
      onChange={setPath}
    />
  );
};
