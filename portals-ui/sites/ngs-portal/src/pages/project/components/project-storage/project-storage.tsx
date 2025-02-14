import { useMemo, useState } from 'react';
import { StoragePath } from './storage-path';

const storages = [
  { pathMask: 's3://epm-aws-dev-etc' },
  { pathMask: 's3://test-fonda' },
  {
    pathMask:
      'nfs://fs-2a5ab373.efs.eu-central-1.amazonaws.com:/home/PIPE_ADMIN',
  },
];

export const ProjectStorage = () => {
  const [path, setPath] = useState('');
  const [selectedStorage, setSelectedStorage] = useState<string>(
    storages[0].pathMask,
  );

  const storageOptions = useMemo(() => {
    return storages.map((storage) => ({
      label: storage.pathMask,
      key: storage.pathMask,
    }));
  }, []);

  return (
    <StoragePath
      storageOptions={storageOptions}
      selectedStorage={selectedStorage}
      onStorageChange={setSelectedStorage}
      selectedStoragePath={path}
      onPathChange={setPath}
    />
  );
};
