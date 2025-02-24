import type { DataStorage } from '@cloud-pipeline/core';
import { Modal, Button } from 'antd';
import { useState, useMemo, useCallback } from 'react';
import { useDataStoragesStore } from '../../state/storages/hooks';
import { StoragesBrowser } from './storages-browser';
import { getItemFullPath } from './utils';
import type { DataStorageItemExtended } from './types.ts';

type Props = {
  value?: string;
  visible: boolean;
  onOk: (selection: string) => void;
  onCancel: () => void;
};

export function StoragesBrowserModal({ visible, onOk, onCancel }: Props) {
  const { pending, data: storages } = useDataStoragesStore();
  const [path, setPath] = useState('');
  const [selectedStorage, setSelectedStorage] = useState<DataStorage | undefined>();
  const [selectedItems, setSelectedItems] = useState<DataStorageItemExtended[]>([]);
  const payload = useMemo(() => selectedItems.map((item) => getItemFullPath(item)).join(','), [selectedItems]);
  const onChangePath = useCallback((newPath?: string) => {
    setPath(newPath ?? '');
  }, []);
  const onChangeStorage = useCallback((storage: DataStorage) => {
    setSelectedStorage(storage);
    setPath('');
  }, []);
  const resetState = () => {
    setPath('');
    setSelectedStorage(undefined);
    setSelectedItems([]);
  };
  return (
    <Modal
      title={'Select folder or file'}
      open={visible}
      onCancel={onCancel}
      okButtonProps={{ disabled: !payload || pending }}
      okText={selectedItems.length ? `Select (${selectedItems.length} items)` : 'Select'}
      width={'80vw'}
      afterClose={resetState}
      centered
      destroyOnClose
      footer={() => (
        <div className="flex gap-1 justify-end">
          <Button onClick={onCancel}>Cancel</Button>
          <Button onClick={() => onOk(payload)} type="primary" disabled={!payload || pending}>
            {selectedItems.length
              ? `Select (${selectedItems.length} item${selectedItems.length > 1 ? 's' : ''})`
              : 'Select'}
          </Button>
          <Button
            type="primary"
            disabled={pending}
            onClick={() => onOk(selectedStorage?.pathMask ?? selectedStorage?.path ?? '')}>
            Select storage
          </Button>
        </div>
      )}>
      <StoragesBrowser
        storages={storages}
        pending={pending}
        path={path}
        onChangePath={onChangePath}
        selectedItems={selectedItems}
        selectedStorage={selectedStorage}
        onChangeStorage={onChangeStorage}
        onSelectionChanged={setSelectedItems}
      />
    </Modal>
  );
}
