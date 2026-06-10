import NotificationBrowser from '../../components/main/notification/NotificationBrowser';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

function NotificationsPage() {
  return <LegacyComponentBridge component={NotificationBrowser} />;
}

export {NotificationsPage};
