import { Select, Tooltip } from 'antd';
import { memo, useCallback } from 'react';

type Option = {
  value: string;
  label: string;
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

    return (
      <div className="flex items-center space-x-1">
        <Select
          mode="multiple"
          style={{ width: '100%', minWidth: '200px' }}
          options={options}
          placeholder="Select..."
          maxTagCount={1}
          prefix={label}
          value={selectedValues}
          onFocus={onFocus}
          onChange={handleValueChange}
          maxTagPlaceholder={(omittedValues) => (
            <Tooltip
              overlayStyle={{ pointerEvents: 'none' }}
              title={omittedValues.map(({ label }) => label).join(', ')}>
              <span>+{selectedValues.length - 1}</span>
            </Tooltip>
          )}
        />
      </div>
    );
  },
);
