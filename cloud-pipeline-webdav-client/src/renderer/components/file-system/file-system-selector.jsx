import React, { useMemo } from 'react';
import PropTypes from 'prop-types';
import { Dropdown } from 'antd';
import { CaretDownOutlined } from '@ant-design/icons';
import {
  useAvailableAdapters,
  useChangeAdapter,
  useFileSystemIdentifier,
} from './hooks/use-file-system';

const Adapter = React.forwardRef((
  {
    className,
    style,
    adapter,
    selected,
    onClick,
  },
  ref,
) => {
  const mergedStyle = useMemo(
    () => (selected ? { ...(style || {}), fontWeight: 'bold' } : style),
    [selected, style],
  );
  return (
    <span
      style={mergedStyle}
      onClick={onClick}
      className={className}
      ref={ref}
    >
      {adapter.name || adapter.identifier || 'Adapter'}
    </span>
  );
});

Adapter.propTypes = {
  adapter: PropTypes.shape({
    name: PropTypes.string,
    identifier: PropTypes.string,
  }).isRequired,
  className: PropTypes.string,
  style: PropTypes.object,
  selected: PropTypes.bool,
  onClick: PropTypes.func,
};

Adapter.defaultProps = {
  className: undefined,
  style: undefined,
  selected: false,
  onClick: undefined,
};

function FileSystemSelector(
  {
    className,
    style,
  },
) {
  const available = useAvailableAdapters();
  const current = useFileSystemIdentifier();
  const onChangeAdapter = useChangeAdapter();
  const currentAdapter = available.find((o) => o.identifier === current);
  if (available.length < 2) {
    return null;
  }
  return (
    <Dropdown
      menu={{
        onClick: ({ key }) => onChangeAdapter(key),
        items: available.map((adapter) => ({
          label: (
            <Adapter
              adapter={adapter}
              selected={adapter === currentAdapter}
            />
          ),
          key: adapter.identifier,
        })),
      }}
      trigger={['click']}
      onClick={(event) => event.stopPropagation()}
    >
      <CaretDownOutlined
        className={className}
        style={style}
      />
    </Dropdown>
  );
}

FileSystemSelector.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
};

FileSystemSelector.defaultProps = {
  className: undefined,
  style: undefined,
};

export default FileSystemSelector;
