import type {ComponentType} from 'react';
import {useLocation} from 'react-router-dom';
import CLIForm from '../../components/settings/CLIForm';
import SystemEvents from '../../components/settings/SystemEvents';
import UserManagementForm from '../../components/settings/UserManagementForm';
import EmailNotificationSettings from '../../components/settings/EmailNotificationSettings';
import Preferences from '../../components/settings/Preferences';
import AWSRegionsForm from '../../components/settings/AWSRegionsForm';
import SystemManagement from '../../components/settings/system-management/system-management';
import SystemDictionaries from '../../components/settings/SystemDictionaries';
import UserProfile from '../../components/settings/user-profile';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

const sectionComponents: Record<string, ComponentType<Record<string, unknown>>> = {
  cli: CLIForm,
  events: SystemEvents,
  user: UserManagementForm,
  email: EmailNotificationSettings,
  preferences: Preferences,
  regions: AWSRegionsForm,
  system: SystemManagement,
  dictionaries: SystemDictionaries,
  profile: UserProfile,
};

function SettingsSectionPage() {
  const {pathname} = useLocation();
  const sectionKey = pathname.split('/').filter(Boolean)[1];
  const Component = sectionKey ? sectionComponents[sectionKey] : undefined;

  if (!Component) {
    return null;
  }

  return <LegacyComponentBridge component={Component as never} />;
}

export {SettingsSectionPage};
