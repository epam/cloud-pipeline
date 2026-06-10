import {useMemo} from 'react';
import type {MetadataValueRendererProps} from './types.ts';
import {parseMuteEmailNotificationValue} from '../../../../models/notifications/CurrentUserNotifications.js';
import {stringifyMetadataValue} from './utilities.ts';

function MuteEmailRenderer(props: MetadataValueRendererProps) {
  const {className, style, value} = props;
  const rawValue = stringifyMetadataValue(value) ?? '';
  const {muted} = useMemo(() => parseMuteEmailNotificationValue(rawValue), [rawValue]);
  return (
    <span className={className} style={style}>
      {muted ? 'Email notifications muted' : 'Email notifications enabled'}
    </span>
  );
}

export {MuteEmailRenderer};
