import React from 'react';
import classNames from 'classnames';
import styles from './icons.css';
import close from './close';
import download from './download';
import loading from './loading';
import file from './file';
import folder from './folder';

const types = {
  close,
  download,
  loading,
  file,
  folder,
};

function Icon(
  {
    className,
    color,
    disabled,
    onClick,
    strokeWidth,
    type,
    width,
  },
) {
  const Svg = types[type];
  if (!Svg) {
    return null;
  }
  const size = width || 20;
  return (
    // eslint-disable-next-line
    <div
      className={
        classNames(
          styles.icon,
          {
            [styles.clickable]: !!onClick && !disabled,
          },
          className,
          {
            [styles.disabled]: disabled,
          },
        )
      }
      onClick={disabled ? null : onClick}
      style={{width: size, height: size}}
    >
      <Svg
        width={strokeWidth || 3}
        color={color}
      />
    </div>
  );
}

export default Icon;
