import {useMemo} from 'react';
import {Popover} from 'antd';
import type {MetadataValueRendererProps} from './types.ts';
import {plural, stringifyMetadataValue} from './utilities.ts';
import {NotificationOutlined} from '@ant-design/icons';

type FsNotification = {
  type?: string;
  value?: number;
  actions?: string[];
};

type FsNotificationRecipient = {
  name?: string;
  principal?: boolean;
};

type FsNotificationsPayload = {
  notifications?: FsNotification[];
  recipients?: FsNotificationRecipient[];
};

function parseFsNotifications(value: string): FsNotificationsPayload {
  try {
    return JSON.parse(value) as FsNotificationsPayload;
  } catch {
    return {};
  }
}

function formatNotification(notification: FsNotification): string {
  const {type = 'GB', value = 0, actions = []} = notification;
  return `${value}${type}:${[...actions].sort().join(',')}`;
}

function FsNotificationsRenderer(props: MetadataValueRendererProps) {
  const {className, style, value} = props;
  const rawValue = stringifyMetadataValue(value) ?? '';
  const {notifications = [], recipients = []} = useMemo(
    () => parseFsNotifications(rawValue),
    [rawValue],
  );
  if (notifications.length === 0) {
    return (
      <span className={className} style={style}>
        Notifications are not configured
      </span>
    );
  }
  const summary = [
    plural(notifications.length, 'notification'),
    recipients.length > 0 ? plural(recipients.length, 'recipient') : undefined,
  ]
    .filter(Boolean)
    .join(', ');
  return (
    <Popover
      content={
        <div style={{maxWidth: '40vw'}}>
          <div>
            <strong>Notifications</strong>
            <ul style={{margin: '4px 0', paddingLeft: 20}}>
              {notifications.map((notification, index) => (
                <li key={index}>{formatNotification(notification)}</li>
              ))}
            </ul>
          </div>
          {recipients.length > 0 && (
            <div>
              <strong>Recipients</strong>
              <ul style={{margin: '4px 0', paddingLeft: 20}}>
                {recipients.map((recipient, index) => (
                  <li key={index}>{recipient.name}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      }
    >
      <span className={className} style={{cursor: 'pointer', ...style}}>
        <NotificationOutlined />
        {summary}
      </span>
    </Popover>
  );
}

export {FsNotificationsRenderer};
