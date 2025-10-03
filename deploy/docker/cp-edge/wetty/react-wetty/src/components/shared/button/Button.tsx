import React from 'react';
import cn from 'classnames';
import styles from './Button.module.css';

export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost';
export type ButtonSize = 'small' | 'medium' | 'large';

export type ButtonProps = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  children: React.ReactNode;
};

const Button: React.FC<ButtonProps> = ({
  variant = 'primary',
  size = 'medium',
  loading = false,
  disabled = false,
  className = '',
  children,
  ...props
}) => {
  const classes = cn(
    styles.btn,
    styles[`btn--${variant}`],
    styles[`btn--${size}`],
    {
      [styles['btn--loading']]: loading,
      [styles['btn--disabled']]: disabled || loading,
    },
    className
  );

  return (
    <button
      className={classes}
      disabled={disabled || loading}
      {...props}
    >
      {loading && (
        <span className={styles.btn__spinner} aria-hidden="true">
          <svg className={styles.btn__spinnerIcon} viewBox="0 0 24 24">
            <circle
              className={styles.btn__spinnerCircle}
              cx="12"
              cy="12"
              r="10"
              stroke="currentColor"
              strokeWidth="2"
              fill="none"
              strokeLinecap="round"
            />
          </svg>
        </span>
      )}
      <span className={cn(styles.btn__content, { [styles['btn__content--loading']]: loading })}>
        {children}
      </span>
    </button>
  );
};

export default Button;
