import type { DataStorage } from '@cloud-pipeline/core';
import { Modal, Button } from 'antd';
import { useState, useMemo, useCallback } from 'react';
import { useDataStoragesStore } from '../../state/storages/hooks';
import type { UIStorageItem } from '../storage-browser/types';
import { StoragesBrowser } from './storages-browser';
import { getItemFullPath } from './utils';

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
  const [items, setItems] = useState<UIStorageItem[]>([]);
  const payload = useMemo(() => {
    if (selectedStorage && items) {
      return items.map((item) => getItemFullPath(selectedStorage, item)).join(',');
    }
    return '';
  }, [selectedStorage, items]);
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
    setItems([]);
  };
  return (
    <Modal
      title={'Select folder or file'}
      open={visible}
      onCancel={onCancel}
      okButtonProps={{ disabled: !payload || pending }}
      okText={items.length ? `Select (${items.length} items)` : 'Select'}
      width={'80vw'}
      afterClose={resetState}
      centered
      destroyOnClose
      footer={() => (
        <div className="flex gap-1 justify-end">
          <Button onClick={onCancel}>Cancel</Button>
          <Button onClick={() => onOk(payload)} type="primary" disabled={!payload || pending}>
            {items.length ? `Select (${items.length} item${items.length > 1 ? 's' : ''})` : 'Select'}
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
        items={items}
        selectedStorage={selectedStorage}
        onChangeStorage={onChangeStorage}
        onSelect={setItems}
      />
    </Modal>
  );
}
