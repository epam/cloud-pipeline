import {useMemo} from 'react';
import dayjs, {toUtcDayjs} from '../../../../utils/dayjs.js';
import displayDate from '../../../../utils/displayDate.js';
import type {MetadataValueRendererProps} from './types.ts';
import {stringifyMetadataValue} from './utilities.ts';

function getDavAccessInfo(value: string) {
  if (!value) {
    return undefined;
  }
  if (!Number.isNaN(Number(value))) {
    const time = toUtcDayjs(new Date(Number(value) * 1000));
    const now = dayjs.utc();
    return {
      available: now.isBefore(time),
      expiresAt: displayDate(time, 'D MMM YYYY, HH:mm'),
    };
  }
  return {
    available: /^true$/i.test(value),
  };
}

function DavMountRenderer(props: MetadataValueRendererProps) {
  const {className, style, value} = props;
  const rawValue = stringifyMetadataValue(value) ?? '';
  const accessInfo = useMemo(() => getDavAccessInfo(rawValue), [rawValue]);
  let label = 'File system access disabled';
  if (accessInfo?.available) {
    label = accessInfo.expiresAt
      ? `File system access enabled till ${accessInfo.expiresAt}`
      : 'File system access enabled';
  }
  return (
    <span className={className} style={style}>
      {label}
    </span>
  );
}

export {DavMountRenderer};
