import type { ChangeEvent } from 'react';
import { Input } from 'antd';
import PrettyName from './pretty-name';
import type { LaunchParameterProps } from './type';
import { validateParameter } from '../../../utils/validators';

export default function StringParameter({
  parameter,
  onChange,
  prettyNameEditable,
  readOnly,
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
          disabled={readOnly}
        />
        {!readOnly && parameter.error ? (
          <span className="pl-4 text-xs text-red-500">{parameter.error}</span>
        ) : null}
      </div>
      <span className="text-faded">{parameter.initial.description}</span>
    </div>
  );
}
