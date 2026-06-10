import {useMemo} from 'react';
import {Popover} from 'antd';
import type {MetadataValueRendererProps} from './types.ts';
import {plural, stringifyMetadataValue} from './utilities.ts';

function RunCapabilitiesRenderer(props: MetadataValueRendererProps) {
  const {className, style, value} = props;
  const rawValue = stringifyMetadataValue(value) ?? '';
  const capabilities = useMemo(
    () =>
      rawValue
        .split(/[,;]/)
        .map((item) => item.trim())
        .filter(Boolean),
    [rawValue],
  );
  if (capabilities.length === 0) {
    return null;
  }
  if (capabilities.length === 1) {
    return (
      <span className={className} style={style}>
        {capabilities[0]}
      </span>
    );
  }
  return (
    <Popover
      content={
        <ul style={{margin: 0, paddingLeft: 20}}>
          {capabilities.map((capability) => (
            <li key={capability}>{capability}</li>
          ))}
        </ul>
      }
    >
      <span className={className} style={{cursor: 'pointer', ...style}}>
        {plural(capabilities.length, 'capability')}
      </span>
    </Popover>
  );
}

export {RunCapabilitiesRenderer};
