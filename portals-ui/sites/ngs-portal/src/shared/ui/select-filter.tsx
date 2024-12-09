import { PickerInput } from '@epam/uui';
import { useArrayDataSource } from '@epam/uui-core';
import { memo, useCallback } from 'react';

type Option = {
  id: string;
  name: string;
  disabled?: boolean;
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
      <div className="flex items-center space-x-1">
        <p>{label}:</p>
        <PickerInput
          dataSource={dataSource}
          value={selectedValues}
          onValueChange={handleValueChange}
          selectionMode="multi"
          getRowOptions={(item) => ({
            isDisabled: item?.disabled,
            checkbox: {
              isDisabled: item?.disabled,
              isVisible: true,
            },
          })}
          placeholder={`Select ${label}`}
          valueType="id"
          maxItems={1}
          onFocus={onFocus}
          isSingleLine
          size="30"
        />
      </div>
    );
  },
);
