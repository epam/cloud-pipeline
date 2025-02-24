import { Button } from 'antd';
import type { MouseEvent as ReactMouseEvent } from 'react';
import { ArrowDownTrayIcon, TrashIcon, PencilIcon } from '@heroicons/react/24/outline';
import type { DataStorageItem } from '@cloud-pipeline/core';
import { DataStorageItemTypes } from '@cloud-pipeline/core';
import { useStorageContext } from '../context/storage-context.ts';
import { useCallback } from 'react';
import type { CommonProps } from '@cloud-pipeline/components';
import classNames from 'classnames';

type Props = CommonProps & {
  item: DataStorageItem;
};

export const RowActions = ({ className, style, item }: Props) => {
  const { onDownloadItem, onDeleteItem, onEditItem } = useStorageContext();

  const onEdit = useCallback(
    (e: ReactMouseEvent<HTMLElement, MouseEvent>) => {
      e.stopPropagation();
      onEditItem(item);
    },
    [onEditItem, item],
  );

  const onDelete = useCallback(
    (e: ReactMouseEvent<HTMLElement, MouseEvent>) => {
      e.stopPropagation();
      onDeleteItem(item);
    },
    [onDeleteItem, item],
  );

  const onDownload = useCallback(
    (e: ReactMouseEvent<HTMLElement, MouseEvent>) => {
      e.stopPropagation();
      onDownloadItem(item);
    },
    [onDownloadItem, item],
  );

  return (
    <div className={classNames('flex', 'justify-end', 'gap-1', className)} style={style}>
      {item.type === DataStorageItemTypes.file && (
        <Button type="text" icon={<ArrowDownTrayIcon className="w-4 h-4" />} onClick={onDownload} />
      )}
      <Button type="text" icon={<PencilIcon className="w-4 h-4" />} onClick={onEdit} />
      <Button type="text" className="ml-1" icon={<TrashIcon className="w-4 h-4 stroke-red-500" />} onClick={onDelete} />
    </div>
  );
};
