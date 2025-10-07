import React, { forwardRef, useId } from "react";
import type { InputHTMLAttributes, ChangeEvent } from "react";
import cn from "classnames";
import styles from "./Input.module.css";

type NativeInputProps = Omit<
  InputHTMLAttributes<HTMLInputElement>,
  "size" | "onChange" | "style"
>;

export type InputSize = "sm" | "md" | "lg";
export type InputType = "text" | "search" | "number" | "color" | "checkbox";

export type BaseInputProps = {
  label?: React.ReactNode;
  name?: string;
  id?: string;
  error?: React.ReactNode | boolean;
  size?: InputSize;
  multiline?: boolean;
  rows?: number;
  endIcon?: React.ReactNode;
  onChange?: (value: string | boolean) => void;
  min?: number;
  max?: number;
  style?: {
    label?: React.CSSProperties;
    input?: React.CSSProperties;
    wrapper?: React.CSSProperties;
  };
};

export type InputProps = BaseInputProps & {
  type?: InputType;
} & NativeInputProps;

const Input = forwardRef<
  HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement,
  InputProps
>(function Input(props, ref) {
  const {
    label,
    id: providedId,
    name,
    error,
    size = "md",
    className,
    disabled,
    required,
    type = "text",
    onChange,
    value,
    min,
    max,
    style = {},
    ...rest
  } = props as InputProps & { className?: string };

  const autoId = useId();
  const id = providedId || `wtm-input-${autoId}`;
  const describedByIds: string[] = [];
  if (error) describedByIds.push(`${id}-error`);

  const rootClass = cn(
    styles.input,
    styles[`input--${size}`],
    {
      [styles["input--error"]]: error,
      [styles["input--disabled"]]: disabled,
    },
    className
  );

  const controlCommonProps = {
    id,
    name,
    disabled,
    required,
    className: styles.inputControl,
  } as const;

  const handleChange = (
    e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
  ) => {
    const target = e.target;
    let value: string | boolean;
    if (type === "checkbox" && target instanceof HTMLInputElement) {
      value = target.checked;
    } else {
      value = target.value;
    }
    if (onChange) {
      onChange(value);
    }
  };

  const handleMinMax = () => {
    let num = Number(value);
    if (
      type !== "number" ||
      value === "" ||
      value === undefined ||
      Number.isNaN(num) ||
      (min === undefined && max === undefined)
    ) {
      return;
    }
    if (min !== undefined && num < min) {
      num = min;
    }
    if (max !== undefined && num > max) {
      num = max;
    }
    if (String(num) !== String(value) && onChange) {
      onChange(String(num));
    }
  };

  const isCheckbox = type === "checkbox";
  const inputProps = isCheckbox
    ? { checked: Boolean(value) }
    : {
        value:
          value === undefined || value === null
            ? type === "color"
              ? "#000000"
              : ""
            : (value as string | number),
      };

  return (
    <div className={rootClass} style={style.wrapper}>
      {label && (
        <label style={style.label} className={styles.inputLabel} htmlFor={id}>
          {label}
          {required && <span>*</span>}
        </label>
      )}
      <input
        type={type}
        {...controlCommonProps}
        onChange={handleChange}
        {...inputProps}
        min={type === "number" ? min : undefined}
        max={type === "number" ? max : undefined}
        onBlur={handleMinMax}
        {...rest}
        style={style.input}
        ref={ref as React.Ref<HTMLInputElement>}
      />
      {!!error && (
        <div id={`${id}-error`} role="alert" className={styles.inputError}>
          {error}
        </div>
      )}
    </div>
  );
});

export default Input;
