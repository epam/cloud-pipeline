import type { ChangeEvent } from 'react';
import { FolderIcon } from '@heroicons/react/24/outline';
import { Input } from 'antd';
import PrettyName from './pretty-name';
import type { LaunchParameterProps } from './type';
import { validateParameter } from '../../../utils/validators';

export default function PathParameter({
  parameter,
  onChange,
  prettyNameEditable,
}: LaunchParameterProps) {
  const onChangeValue = (event: ChangeEvent<HTMLInputElement>) => {
    onChange(parameter.key, {
      ...parameter,
      value: event.target.value,
      ...validateParameter(parameter, event.target.value),
    });
  };
  return (
    <div className="flex flex-col gap-1">
      <PrettyName
        editable={prettyNameEditable}
        parameter={parameter}
        onChange={onChange}
      />
      <div>
        <Input
          value={String(parameter.value || '')}
          status={parameter.error ? 'error' : undefined}
          onChange={onChangeValue}
          addonBefore={<FolderIcon className="w-4 h-4" />}
        />
        {parameter.error ? (
          <span className="pl-4 text-xs text-red-500">{parameter.error}</span>
        ) : null}
      </div>
      <span className="text-faded">{parameter.initial.description}</span>
    </div>
  );
}
