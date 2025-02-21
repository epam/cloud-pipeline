import { useCallback, useMemo, useState } from 'react';
import { Empty, Input, Splitter, Table } from 'antd';
import type { ChangeEvent } from 'react';
import { MagnifyingGlassIcon } from '@heroicons/react/24/outline';
import type { DataStorage } from '@cloud-pipeline/core';
import { StorageBrowser } from '../storage-browser';
import type { UIStorageItem } from '../storage-browser/types';
import HighlightedText from '../../shared/highlight-text';
import './styles.css';

type Props = {
  storages: DataStorage[];
  pending: boolean;
  path: string;
  onChangePath: (path?: string) => void;
  items: UIStorageItem[];
  selectedStorage: DataStorage | undefined;
  onChangeStorage: (storage: DataStorage) => void;
  onSelect: (items: UIStorageItem[]) => void;
  search?: string;
  onChangeSearch?: (search: string) => void;
};

export function StoragesBrowser({
  storages,
  pending,
  path,
  onChangePath,
  items,
  selectedStorage,
  onChangeStorage,
  onSelect,
}: Props) {
  const [search, setSearch] = useState<string>('');
  const onSearch = useCallback((event: ChangeEvent<HTMLInputElement>) => {
    setSearch(event.target.value);
  }, []);
  const filteredData = useMemo(
    () =>
      storages.filter((storage) => {
        return storage.name.toLowerCase().includes((search ?? '').toLowerCase());
      }),
    [search, storages],
  );
  const columns = useMemo(
    () => [
      {
        dataIndex: 'name',
        key: 'name',
        render: (value: string) => <HighlightedText search={search}>{value}</HighlightedText>,
      },
    ],
    [search],
  );
  const selectionConfig = useMemo(
    () => ({
      selectedRowKeys: selectedStorage ? [selectedStorage.id] : [],
      hideSelectAll: true,
      onChange: (_: React.Key[], selectedRows: DataStorage[]) => {
        const last = selectedRows.pop();
        if (last) {
          onChangeStorage(last);
        }
      },
    }),
    [onChangeStorage, selectedStorage],
  );
  return (
    <Splitter className="storages-browser overflow-hidden p-2 h-[60vh]">
      <Splitter.Panel style={{ display: 'flex' }} defaultSize="40%" min="20%" max="70%">
        <div className="flex flex-col gap-1">
          <Input
            prefix={<MagnifyingGlassIcon className="w-4 h-4" />}
            placeholder="Search"
            disabled={pending}
            value={search}
            onChange={onSearch}
          />
          <Table
            columns={columns}
            dataSource={filteredData}
            onRow={(record) => ({
              onClick: () => onChangeStorage(record),
            })}
            loading={pending}
            size="small"
            pagination={false}
            showHeader={false}
            rowClassName="cursor-pointer"
            rowSelection={selectionConfig}
            scroll={{ y: '100vh' }}
            rowKey={(record) => record.id}
            style={{ minWidth: '20%' }}
          />
        </div>
      </Splitter.Panel>
      <Splitter.Panel style={{ display: 'flex' }}>
        {selectedStorage ? (
          <StorageBrowser
            storageId={selectedStorage?.id}
            path={path}
            onPathChange={onChangePath}
            className="flex-1 overflow-auto"
            selection={items}
            onSelectItem={onSelect}
          />
        ) : (
          <Empty className="flex flex-1 flex-col justify-center items-center" />
        )}
      </Splitter.Panel>
    </Splitter>
  );
}
