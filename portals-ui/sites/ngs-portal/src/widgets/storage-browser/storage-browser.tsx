import classNames from 'classnames';
import type { FindSingleDataStorageCriteria } from '@cloud-pipeline/core';
import { HeaderActions, StorageContentList } from './components';
import type { CommonProps } from '@cloud-pipeline/components';
import { useDataStorage } from '../../state/storages/hooks.ts';
import { StorageContextProvider } from './context/provider.tsx';
import type { UIStorageItem } from './types';
import { Empty } from 'antd';

type Props = CommonProps & {
  storageId: FindSingleDataStorageCriteria;
  path?: string;
  onPathChange?: (path?: string) => void;
  showHeaderControls?: boolean;
  selection?: UIStorageItem[];
  onSelectItem?: (selection: UIStorageItem[]) => void;
  pending?: boolean;
};

export function StorageBrowser({
  className,
  style,
  storageId: storageIdCriteria,
  path,
  onPathChange,
  showHeaderControls,
  selection,
  onSelectItem,
  pending: pendingProp,
}: Props) {
  const storage = useDataStorage(storageIdCriteria);

  if (!storage) {
    return <div>Storage not found</div>;
  }

  return (
    <div className={classNames(className, 'inline-flex', 'flex-col', 'gap-2', 'overflow-hidden')} style={style}>
      <StorageContextProvider storageId={storageIdCriteria} path={path} onPathChange={onPathChange}>
        {showHeaderControls ? <HeaderActions /> : null}
        <StorageContentList selection={selection}
                            onSelectItem={onSelectItem} />
      </StorageContextProvider>
    </div>
  );
}
