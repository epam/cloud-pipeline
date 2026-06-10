import {useCallback, useEffect, useMemo, useRef, useState} from 'react';

export function useChangeCallback<T>(
  value: T | undefined,
  onChange: ((value: T | undefined) => void) | undefined,
): [T | undefined, (value: T | undefined) => void] {
  const [aValue, setValue] = useState(value);
  const valueRef = useRef(value);
  useEffect(() => {
    if (valueRef.current !== value) {
      setValue(value);
    }
  }, [value, setValue]);
  const onChangeCallback = useCallback(
    (newValue: T) => {
      if (onChange) {
        onChange(newValue);
      }
      setValue(newValue);
    },
    [onChange],
  );
  return useMemo(() => [aValue, onChangeCallback], [aValue, onChangeCallback]);
}
