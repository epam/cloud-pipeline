import React from 'react';
import Selection from './selection';

export default function SettingValue(
  {
    config,
    value,
    onChange,
    dependency,
    filter
  }
) {
  if (!config) {
    return null;
  }
  if (/^(radio|multi-selection)$/i.test(config.type)) {
    return (
      <Selection
        config={config}
        value={value}
        onChange={onChange}
        dependency={dependency}
        multiple={/^multi-selection$/i.test(config.type)}
        filter={filter}
      />
    );
  }
  return null;
}
