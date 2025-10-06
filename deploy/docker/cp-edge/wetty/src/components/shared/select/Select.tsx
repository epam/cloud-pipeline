import React, { forwardRef, useId } from 'react';
import cn from 'classnames';
import styles from './Select.module.css';

export type SelectSize = 'sm' | 'md' | 'lg';
export type SelectVariant = 'default' | 'outlined' | 'filled';

export interface SelectOption {
  value: string | number;
  label: string;
  disabled?: boolean;
}

export interface SelectProps extends Omit<React.SelectHTMLAttributes<HTMLSelectElement>, 'size' | 'onChange' | 'style'> {
  label?: React.ReactNode;
  size?: SelectSize;
  variant?: SelectVariant;
  fullWidth?: boolean;
  options: SelectOption[];
  placeholder?: string;
  onChange?: (value: string | number) => void;
  wrapperClassName: string;
  style: { label?: React.CSSProperties; input?: React.CSSProperties }
}

const Select = forwardRef<HTMLSelectElement, SelectProps>(({
  label,
  size = 'md',
  variant = 'default',
  fullWidth = false,
  options = [],
  placeholder,
  disabled = false,
  required = false,
  className,
  wrapperClassName,
  id: providedId,
  name,
  value,
  onChange,
  style = {},
  ...props
}, ref) => {
  const autoId = useId();
  const id = providedId || `select-${autoId}`;
  const handleChange = (event: React.ChangeEvent<HTMLSelectElement>) => {
    if (onChange) {
      onChange(event.target.value);
    }
  };
  return (
    <div className={cn(styles.selectWrapper, wrapperClassName)}>
      {label && (
        <label className={styles.selectLabel} htmlFor={id} style={style.label}>
          {label}
          {required && <span className={styles.selectRequired}>*</span>}
        </label>
      )}
      <div className={styles.selectContainer} style={style.input}>
        <select
          ref={ref}
          id={id}
          name={name}
          value={value}
          disabled={disabled}
          required={required}
          onChange={handleChange}
          className={cn(
            styles.select,
            styles[`select--${size}`],
            styles[`select--${variant}`],
            {
              [styles['select--disabled']]: disabled,
              [styles['select--full-width']]: fullWidth,
            },
            className
          )}
          {...props}
        >
          {placeholder && (
            <option value="" disabled>
              {placeholder}
            </option>
          )}
          {options.map((option) => (
            <option
              key={String(option.value)}
              value={option.value}
              disabled={option.disabled}
            >
              {option.label}
            </option>
          ))}
        </select>
        <div className={styles.selectIcon}>
          <svg className={styles.selectChevron} viewBox="0 0 24 24" fill="none">
            <path
              d="M8 10L12 14L16 10"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </div>
      </div>
    </div>
  );
});

export default Select;
