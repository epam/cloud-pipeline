import { ChangeEvent, ChangeEventHandler, useCallback } from 'react';

export function useInputChange(
  onChange?: (value: string) => void,
): ChangeEventHandler<HTMLInputElement> {
  return useCallback(
    (event: ChangeEvent<HTMLInputElement>) => {
      if (onChange) {
        onChange(event.target.value);
      }
    },
    [onChange],
  );
}
