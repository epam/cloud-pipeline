import classNames from 'classnames';
import type { FindSingleDataStorageCriteria } from '@cloud-pipeline/core';
import { HeaderActions, StorageContentList } from './components';
import type { CommonProps } from '@cloud-pipeline/components';
import { useDataStorage } from '../../state/storages/hooks.ts';
import { StorageContextProvider } from './context/provider.tsx';

type Props = CommonProps & {
  storageId: FindSingleDataStorageCriteria;
  path?: string;
  onPathChange?: (path?: string) => void;
  showHeaderControls?: boolean;
};

export function StorageBrowser({
  className,
  style,
  storageId: storageIdCriteria,
  path,
  onPathChange,
  showHeaderControls,
}: Props) {
  const storage = useDataStorage(storageIdCriteria);

  if (!storage) {
    return <div>Storage not found</div>;
  }

  return (
    <div className={classNames(className, 'inline-flex', 'flex-col', 'gap-2', 'overflow-hidden')} style={style}>
      <StorageContextProvider storageId={storageIdCriteria} path={path} onPathChange={onPathChange}>
        {showHeaderControls ? <HeaderActions /> : null}
        <StorageContentList />
      </StorageContextProvider>
    </div>
  );
}
