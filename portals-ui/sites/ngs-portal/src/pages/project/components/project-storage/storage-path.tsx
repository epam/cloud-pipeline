import { useState } from 'react';
import { Select, Input, Button, message } from 'antd';
import { folderUrlPattern } from '../../../../shared/constants/patterns';

const storageProtocols = ['nfs', 's3', 'gcs', 'azure'];

type Props = {
  storageOptions: { label: string; value: string }[];
  onChange: (value: string) => void;
  onStorageChange: (value: string) => void;
  selectedStorage?: string;
  path?: string;
};

export const StoragePath = ({
  storageOptions,
  onChange,
  selectedStorage,
  onStorageChange,
  path = '',
}: Props) => {
  const [rawValue, setRawValue] = useState('');
  const [isEditing, setIsEditing] = useState(false);
  const [messageApi, contextHolder] = message.useMessage();

  const handleInputChange = (value: string) => {
    setRawValue(value);
  };

  const handleBlur = () => {
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
        (opt) => opt.value === fullStorageUrl,
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

    if (processedValue.length && !folderUrlPattern.test(processedValue)) {
      messageApi.open({
        key: 'path-enter',
        type: 'error',
        content: 'Only letters, numbers, "_", "-", ".", and "/" are allowed',
        duration: 3,
      });
      return;
    }

    if (!processedValue.endsWith('/')) {
      processedValue += '/';
    }

    onChange(processedValue);
    setIsEditing(false);
  };

  return (
    <div className="flex gap-2 items-center w-full">
      {contextHolder}
      <Select
        className="w-[250px]"
        value={selectedStorage}
        options={storageOptions}
        onChange={onStorageChange}
      />
      {isEditing ? (
        <Input
          className="flex-grow px-4"
          style={{ flex: 1 }}
          value={rawValue}
          onChange={(e) => handleInputChange(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              handleBlur();
            }
          }}
          onBlur={handleBlur}
          autoFocus
        />
      ) : (
        <Button
          className="flex-grow justify-start px-4"
          type="text"
          onClick={() => {
            setRawValue(path === '/' ? '' : path);
            setIsEditing(true);
          }}>
          {path || '/'}
        </Button>
      )}
    </div>
  );
};
