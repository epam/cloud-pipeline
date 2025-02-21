import { Button } from 'antd';
import { ArrowDownTrayIcon, TrashIcon, PencilIcon } from '@heroicons/react/24/outline';
import type { DataStorageItem } from '@cloud-pipeline/core';
import { DataStorageItemTypes } from '@cloud-pipeline/core';

type Props = {
  item: DataStorageItem;
  storageId: number;
  onResetPaging: () => void;
  onEdit: (key: DataStorageItemTypes, name: string) => void;
  onDelete: (key: DataStorageItemTypes, name: string, path: string) => void;
  onDownload: (name: string, path: string) => void;
};

export const RowActions = ({ item, onEdit, onDelete, onDownload }: Props) => {
  const { type, name, path } = item;

  const handleEdit = (e: React.MouseEvent<HTMLElement, MouseEvent>) => {
    e.stopPropagation();
    onEdit(type, name);
  };

  const handleDelete = (e: React.MouseEvent<HTMLElement, MouseEvent>) => {
    e.stopPropagation();
    onDelete(type, name, path);
  };

  const handleDownload = (e: React.MouseEvent<HTMLElement, MouseEvent>) => {
    e.stopPropagation();
    onDownload(name, path);
  };

  return (
    <div className="flex justify-end gap-1">
      {type === DataStorageItemTypes.file && (
        <Button type="text" icon={<ArrowDownTrayIcon className="w-4 h-4" />} onClick={handleDownload} />
      )}
      <Button type="text" icon={<PencilIcon className="w-4 h-4" />} onClick={handleEdit} />
      <Button
        type="text"
        className="ml-1"
        icon={<TrashIcon className="w-4 h-4 stroke-red-500" />}
        onClick={handleDelete}
      />
    </div>
  );
};
