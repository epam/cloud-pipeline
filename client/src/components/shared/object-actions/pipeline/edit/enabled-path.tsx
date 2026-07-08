import {Checkbox, Input} from 'antd';
import type {InputRef} from 'antd';
import type {CheckboxChangeEvent} from 'antd/es/checkbox';
import type {ChangeEvent, CSSProperties} from 'react';
import {useCallback, useEffect, useRef, useState} from 'react';

type EnabledPathProps = {
  value?: string;
  onChange?: (value: string) => void;
  disabled?: boolean;
  defaultPathValue?: string;
  placeholder?: string;
  className?: string;
  style?: CSSProperties;
};

function EnabledPath({
  value,
  onChange,
  disabled,
  defaultPathValue,
  placeholder,
  className,
  style,
}: EnabledPathProps) {
  const [enabled, setEnabled] = useState(() => !!(value && value.length > 0));
  const [innerValue, setInnerValue] = useState(value ?? '');
  const inputRef = useRef<InputRef>(null);
  // Track the last value we reported via onChange so we can distinguish
  // parent-driven changes (form reset) from our own echoed-back values.
  const reportedRef = useRef<string | undefined>(value);

  useEffect(() => {
    if (value !== reportedRef.current) {
      reportedRef.current = value;
      setInnerValue(value ?? '');
      setEnabled(!!(value && value.length > 0));
    }
  }, [value]);

  const report = useCallback(
    (isEnabled: boolean, val: string) => {
      const reported = isEnabled ? val : '';
      reportedRef.current = reported;
      onChange?.(reported);
    },
    [onChange],
  );

  const onToggle = useCallback(
    (e: CheckboxChangeEvent) => {
      const checked = e.target.checked;
      if (checked) {
        const restored = defaultPathValue ?? innerValue;
        setEnabled(true);
        setInnerValue(restored);
        report(true, restored);
        setTimeout(() => inputRef.current?.focus(), 0);
      } else {
        setEnabled(false);
        report(false, innerValue);
      }
    },
    [defaultPathValue, innerValue, report],
  );

  const onInputChange = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => {
      const val = e.target.value;
      setInnerValue(val);
      report(enabled, val);
    },
    [enabled, report],
  );

  const onBlur = useCallback(() => {
    if (enabled && !innerValue) {
      setInnerValue('/');
      report(true, '/');
    }
  }, [enabled, innerValue, report]);

  return (
    <div
      className={className}
      style={{display: 'flex', flexDirection: 'row', alignItems: 'center', width: '100%', ...style}}
    >
      <Checkbox disabled={disabled} checked={enabled} onChange={onToggle}>
        Enabled
      </Checkbox>
      <Input
        ref={inputRef}
        style={{flex: 1, marginLeft: 5, display: enabled ? undefined : 'none'}}
        disabled={disabled}
        value={innerValue}
        placeholder={placeholder}
        onBlur={onBlur}
        onChange={onInputChange}
      />
    </div>
  );
}

export {EnabledPath};
