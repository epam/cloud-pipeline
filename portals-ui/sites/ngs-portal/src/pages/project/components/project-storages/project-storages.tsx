import { StorageBrowser } from '../../../../widgets/storage-browser';
import { StoragePath } from '../../../../widgets/storage-path';
import { useCallback, useState } from 'react';

const STORAGE_ID_MOCK = 7673;

export function ProjectStorages() {
  const [path, setPath] = useState('');
  const onChangePath = useCallback(
    (newPath?: string) => {
      setPath(newPath ?? '');
    },
    [setPath],
  );
  return (
    <div className="h-full flex flex-col overflow-hidden">
      <div className="flex-shrink-0">
        <StoragePath storage={STORAGE_ID_MOCK} path={path} onPathChange={onChangePath} />
      </div>
      <StorageBrowser storageId={STORAGE_ID_MOCK} path={path} showHeaderControls className="flex-1 overflow-auto" />
    </div>
  );
}
