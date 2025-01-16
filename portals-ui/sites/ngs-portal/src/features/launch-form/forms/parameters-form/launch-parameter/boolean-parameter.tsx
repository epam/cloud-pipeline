import { Checkbox } from 'antd';
import type { CheckboxChangeEvent } from 'antd';
import type { LaunchParameterProps } from './type';
import PrettyName from './pretty-name';
import { validateParameter } from '../../../utils/validators';

export default function BooleanParameter({
  parameter,
  onChange,
  prettyNameEditable,
}: LaunchParameterProps) {
  const onChangeValue = (event: CheckboxChangeEvent) => {
    onChange(parameter.key, {
      ...parameter,
      value: event.target.checked,
      ...validateParameter(parameter, event.target.checked),
    });
  };
  return (
    <div className="flex flex-col gap-1">
      <PrettyName
        showRequiredMark={false}
        parameter={parameter}
        onChange={onChange}
        editable={prettyNameEditable}
      />
      <Checkbox checked={parameter.value === true} onChange={onChangeValue}>
        Enabled
      </Checkbox>
      {parameter.error ? (
        <span className="pl-4 text-xs text-red-500">{parameter.error}</span>
      ) : null}
      <span className="text-faded">{parameter.initial.description}</span>
    </div>
  );
}
