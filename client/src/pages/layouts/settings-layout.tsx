import {Outlet, useLocation, useNavigate} from 'react-router-dom';
import {Menu} from 'antd';
import type {MenuProps} from 'antd';
import styles from '../../components/settings/styles.module.css';
import layoutTabsStyles from './layout-tabs.module.css';
import {routeingPaths as paths} from '../../routing/paths.ts';

const sections = [
  {key: 'cli', label: 'CLI', path: paths.settingsSection('cli')},
  {key: 'events', label: 'Events', path: paths.settingsSection('events')},
  {key: 'user', label: 'Users', path: paths.settingsSection('user')},
  {key: 'email', label: 'Email', path: paths.settingsSection('email')},
  {key: 'preferences', label: 'Preferences', path: paths.settingsSection('preferences')},
  {key: 'regions', label: 'Regions', path: paths.settingsSection('regions')},
  {key: 'system', label: 'System', path: paths.settingsSection('system')},
  {key: 'dictionaries', label: 'Dictionaries', path: paths.settingsSection('dictionaries')},
  {key: 'profile', label: 'Profile', path: paths.settingsSection('profile')},
] as const;

function SettingsLayout() {
  const {pathname} = useLocation();
  const navigate = useNavigate();
  const activeKey = pathname.split('/').filter(Boolean)[1] ?? 'cli';

  const onClick: MenuProps['onClick'] = ({key}) => {
    const section = sections.find((item) => item.key === key);
    if (section) {
      navigate(section.path);
    }
  };

  return (
    <div className="flex h-full flex-col">
      <div className={styles.rowMenu}>
        <Menu
          mode="horizontal"
          selectedKeys={[activeKey]}
          className={`${styles.tabsMenu} ${layoutTabsStyles.tabsMenu}`}
          onClick={onClick}
          items={sections.map((section) => ({
            key: section.key,
            label: section.label,
          }))}
        />
      </div>
      <section className="min-h-0 flex-1 overflow-auto">
        <Outlet />
      </section>
    </div>
  );
}

export {SettingsLayout};
