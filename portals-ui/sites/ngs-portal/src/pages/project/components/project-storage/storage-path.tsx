import { useCallback, useMemo, useState } from 'react';
import { Input, Button, message, Breadcrumb, Dropdown } from 'antd';
import { ChevronDownIcon } from '@heroicons/react/24/outline';

const storageProtocols = ['nfs', 's3', 'gcs', 'azure'];

type Props = {
  onPathChange: (value: string) => void;
  onStorageChange: (value: string) => void;
  selectedStorage: string;
  storageOptions: { label: string; key: string }[];
  selectedStoragePath?: string;
};

export const StoragePath = ({
  storageOptions,
  onPathChange,
  selectedStorage,
  onStorageChange,
  selectedStoragePath = '',
}: Props) => {
  const [rawValue, setRawValue] = useState('');
  const [isEditing, setIsEditing] = useState(false);
  const [messageApi, contextHolder] = message.useMessage();

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setRawValue(e.target.value);
  };

  const leaveEditMode = useCallback(() => {
    const normalizedValue = rawValue.trim().toLowerCase();
    let processedValue = normalizedValue.replace(/\/{2,}/g, '/');

    const match = /^([a-z0-9]+):\/\/([^/]+)(\/.*)?$/.exec(normalizedValue);

    if (match) {
      const protocol = match[1];
      const bucketName = match[2];
      const path = match[3] || '/';

      if (!storageProtocols.includes(protocol)) {
        messageApi.open({
          key: 'path-enter',
          type: 'error',
          content: `Unknown storage: "${protocol}"`,
        });
        return;
      }

      const fullStorageUrl = `${protocol}://${bucketName}`;
      const foundStorage = storageOptions.find(
        (opt) => opt.key === fullStorageUrl,
      );

      if (foundStorage) {
        onStorageChange(fullStorageUrl);
        processedValue = path;
      } else {
        messageApi.open({
          key: 'path-enter',
          type: 'error',
          content: `Storage "${fullStorageUrl}" is not in the list of available storages`,
        });
        return;
      }
    }

    if (!processedValue.endsWith('/')) {
      processedValue += '/';
    }

    onPathChange(processedValue);
    setIsEditing(false);
  }, [messageApi, onPathChange, onStorageChange, rawValue, storageOptions]);

  const enterEditMode = useCallback(() => {
    setRawValue(selectedStoragePath === '/' ? '' : selectedStoragePath);
    setIsEditing(true);
  }, [selectedStoragePath]);

  const handleBreadcrumbClick = useCallback(
    (e: React.MouseEvent<HTMLElement, MouseEvent>, i: number) => {
      e.stopPropagation();
      const newPath = selectedStoragePath
        .split('/')
        .filter(Boolean)
        .slice(0, i + 1)
        .join('/');

      onPathChange(`/${newPath}/`);
    },
    [onPathChange, selectedStoragePath],
  );

  const resetPath = useCallback(() => {
    onPathChange('/');
  }, [onPathChange]);

  const handleInputKeydown = useCallback(
    (e: React.KeyboardEvent<HTMLInputElement>) => {
      if (e.key === 'Enter') {
        leaveEditMode();
      }
    },
    [leaveEditMode],
  );

  const breadcrumbs = useMemo(() => {
    return selectedStoragePath
      .split('/')
      .filter(Boolean)
      .map((item, i, arr) => ({
        key: item,
        title:
          i === arr.length - 1 ? (
            item
          ) : (
            <Button
              className="p-0"
              type="link"
              onClick={(e) => handleBreadcrumbClick(e, i)}>
              {item}
            </Button>
          ),
      }));
  }, [handleBreadcrumbClick, selectedStoragePath]);

  return (
    <div className="flex gap-1 items-center w-full">
      {contextHolder}
      <div className="flex items-center cursor-pointer">
        <Button type="link" className="p-0" onClick={resetPath}>
          {selectedStorage}
        </Button>

        <Dropdown
          className="ml-2"
          menu={{
            items: storageOptions,
            onClick: ({ key }) => onStorageChange(key),
          }}
          trigger={['click']}>
          <Button type="text" className="p-1">
            <ChevronDownIcon className="w-4 h-4" />
          </Button>
        </Dropdown>
      </div>
      {isEditing ? (
        <Input
          className="flex-grow px-4"
          style={{ flex: 1 }}
          value={rawValue}
          onChange={handleInputChange}
          onKeyDown={handleInputKeydown}
          onBlur={leaveEditMode}
          autoFocus
        />
      ) : (
        <Button
          className="flex-grow justify-start px-4 cursor-text"
          type="text"
          onClick={enterEditMode}>
          {breadcrumbs.length ? (
            <Breadcrumb
              className="[&_li]:flex [&_li]:items-center"
              items={breadcrumbs}
            />
          ) : (
            '/'
          )}
        </Button>
      )}
    </div>
  );
};
