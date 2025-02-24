import { useCallback, useState, type ChangeEvent } from 'react';
import { FolderIcon } from '@heroicons/react/24/outline';
import { Button, Input, Space } from 'antd';
import PrettyName from './pretty-name';
import type { LaunchParameterProps } from './type';
import { validateParameter } from '../../../utils/validators';
import { StoragesBrowserModal } from '../../../../../widgets/storages-browser';

export default function PathParameter({ parameter, onChange, prettyNameEditable, readOnly }: LaunchParameterProps) {
  const [modalVisible, setModalVisible] = useState(false);
  const onChangeValue = useCallback(
    (event: ChangeEvent<HTMLInputElement>) => {
      onChange(parameter.key, {
        ...parameter,
        value: event.target.value,
        ...validateParameter(parameter, event.target.value),
      });
    },
    [onChange, parameter],
  );
  const onSelectValue = useCallback(
    (selection: string) => {
      onChange(parameter.key, {
        ...parameter,
        value: selection,
        ...validateParameter(parameter, selection),
      });
      setModalVisible(false);
    },
    [onChange, parameter],
  );
  return (
    <div className="flex flex-col gap-1">
      <PrettyName editable={prettyNameEditable} parameter={parameter} onChange={onChange} />
      <div>
        <Space.Compact style={{ width: '100%' }}>
          <Button onClick={() => setModalVisible(true)}>
            <FolderIcon className="w-4 h-4" />
          </Button>
          <Input
            value={String(parameter.value || '')}
            status={parameter.error ? 'error' : undefined}
            onChange={onChangeValue}
            disabled={readOnly}
          />
        </Space.Compact>
        {!readOnly && parameter.error ? <span className="pl-4 text-xs text-red-500">{parameter.error}</span> : null}
      </div>
      <span className="text-faded">{parameter.initial.description}</span>
      <StoragesBrowserModal
        value={parameter.value as string}
        onOk={onSelectValue}
        onCancel={() => setModalVisible(false)}
        visible={modalVisible}
      />
    </div>
  );
}
