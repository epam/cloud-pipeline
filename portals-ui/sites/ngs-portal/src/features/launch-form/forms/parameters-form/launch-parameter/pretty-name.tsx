import { useState } from 'react';
import { Input } from 'antd';
import { PencilIcon } from '@heroicons/react/24/outline';
import type { ChangeEvent } from 'react';
import type { LaunchParameterProps } from './type';
import classNames from 'classnames';

type PrettyNameProps = LaunchParameterProps & {
  showRequiredMark?: boolean;
  editable?: boolean;
};

export default function PrettyName({
  parameter,
  onChange,
  showRequiredMark = true,
  editable = false,
}: PrettyNameProps) {
  const [editMode, setEditMode] = useState(false);
  const toggleEditMode = (expanded?: boolean) => {
    const mode = expanded ?? !editMode;
    setEditMode(mode);
  };
  const onChangeKey = (event: ChangeEvent<HTMLInputElement>) => {
    const value = event.target.value;
    onChange(parameter.key, {
      ...parameter,
      key: event.target.value,
      touched: value !== parameter.initialKey,
      keyError: value.length ? undefined : 'Parameter key is required.',
    });
  };
  const onChangePrettyName = (event: ChangeEvent<HTMLInputElement>) => {
    const value = event.target.value;
    onChange(parameter.key, {
      ...parameter,
      pretty_name: event.target.value,
      touched: value !== parameter.initial.pretty_name,
    });
  };
  const renderExpanded = () => (
    <div className="flex gap-2">
      <Input
        value={parameter.key}
        onChange={onChangeKey}
        onPressEnter={() => toggleEditMode()}
        prefix={<span className="text-xs text-faded">Name:</span>}
      />
      <Input
        value={parameter.pretty_name}
        onChange={onChangePrettyName}
        onPressEnter={() => toggleEditMode()}
        prefix={<span className="text-xs text-faded">Pretty name:</span>}
      />
    </div>
  );
  return (
    <div
      onFocus={(e) => {
        if (!editable) {
          return;
        }
        const currentTarget = e.currentTarget;
        setTimeout(() => {
          const focused = document.activeElement;
          if (focused && currentTarget.contains(focused)) {
            toggleEditMode(true);
          }
        });
      }}
      onBlur={(e) => {
        if (!editable) {
          return;
        }
        const currentTarget = e.currentTarget;
        setTimeout(() => {
          const focused = document.activeElement;
          if (!focused || !currentTarget.contains(focused)) {
            toggleEditMode(false);
          }
        });
      }}
      tabIndex={0}
      className={classNames('flex gap-2 h-8 items-center w-fit', {
        'cursor-pointer': editable,
      })}>
      {editMode ? (
        renderExpanded()
      ) : (
        <>
          {showRequiredMark && parameter.initial.required ? (
            <span className="text-red-500">*</span>
          ) : null}
          {parameter.key ? (
            <span>{parameter.pretty_name ?? parameter.key}</span>
          ) : (
            <span className="text-faded">
              {'<Parameter Name>'}
              <span className="text-red-500 text-sm"> - required</span>
            </span>
          )}
          {editable ? <PencilIcon className="w-4 h-4" /> : null}
        </>
      )}
    </div>
  );
}
