import { PickerInput } from '@epam/uui';
import { useArrayDataSource } from '@epam/uui-core';
import { memo, useCallback } from 'react';

type Option = {
  id: string;
  name: string;
};

type Props = {
  options: Option[];
  selectedValues: string[];
  onChange: (selectedItems?: string[]) => void;
  label: string;
  onFocus?: (e: React.FocusEvent<HTMLElement, Element>) => void;
};

export const SelectFilter = memo(
  ({ options, selectedValues, onChange, label, onFocus }: Props) => {
    const handleValueChange = useCallback(
      (selectedItems: string[]) => {
        onChange(selectedItems);
      },
      [onChange],
    );

    const dataSource = useArrayDataSource<
      Option | undefined,
      string | undefined,
      unknown
    >(
      {
        items: options,
      },
      [],
    );

    return (
      <PickerInput
        dataSource={dataSource}
        value={selectedValues}
        onValueChange={handleValueChange}
        selectionMode="multi"
        placeholder={label}
        valueType="id"
        maxItems={1}
        onFocus={onFocus}
        isSingleLine
      />
    );
  },
);
