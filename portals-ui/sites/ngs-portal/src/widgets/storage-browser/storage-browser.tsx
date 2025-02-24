import classNames from 'classnames';
import type { DataStorageItem, FindSingleDataStorageCriteria } from '@cloud-pipeline/core';
import { HeaderActions, StorageContentList } from './components';
import type { CommonProps } from '@cloud-pipeline/components';
import { useDataStorage } from '../../state/storages/hooks.ts';
import { StorageContextProvider } from './context/provider.tsx';

type Props = CommonProps & {
  storageId: FindSingleDataStorageCriteria;
  path?: string;
  onPathChange?: (path?: string) => void;
  showHeaderControls?: boolean;
  showItemActions?: boolean;
  selectedItems?: DataStorageItem[];
  onSelectionChanged?: (selection: DataStorageItem[]) => void;
  pending?: boolean;
};

export function StorageBrowser({
  className,
  style,
  storageId: storageIdCriteria,
  path,
  onPathChange,
  showHeaderControls,
  showItemActions,
  selectedItems,
  onSelectionChanged,
  pending: pendingProp,
}: Props) {
  const storage = useDataStorage(storageIdCriteria);

  if (!storage) {
    return <div>Storage not found</div>;
  }

  return (
    <div className={classNames(className, 'inline-flex', 'flex-col', 'gap-2', 'overflow-hidden')} style={style}>
      <StorageContextProvider
        storageId={storageIdCriteria}
        path={path}
        onPathChange={onPathChange}
        selectedItems={selectedItems}
        onSelectionChanged={onSelectionChanged}>
        {showHeaderControls ? <HeaderActions /> : null}
        <StorageContentList pending={pendingProp} showItemActions={showItemActions} />
      </StorageContextProvider>
    </div>
  );
}
