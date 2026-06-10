import {Outlet, useLocation, useNavigate} from 'react-router-dom';
import {Menu} from 'antd';
import type {MenuProps} from 'antd';
import styles from '../../components/cluster/Cluster.module.css';
import layoutTabsStyles from './layout-tabs.module.css';
import {routeingPaths as paths} from '../../routing/paths.ts';

const sections = [
  {key: 'default', label: 'All Nodes', path: paths.cluster},
  {key: 'core-nodes', label: 'Core nodes', path: paths.clusterCoreNodes},
  {key: 'cloud-nodes', label: 'Cloud nodes', path: paths.clusterCloudNodes},
  {key: 'hot', label: 'Hot node pool', path: paths.clusterHot},
  {key: 'usage', label: 'Usage', path: paths.clusterUsage},
] as const;

function ClusterLayout() {
  const {pathname} = useLocation();
  const navigate = useNavigate();
  const activeKey = pathname.split('/').filter(Boolean)[1] ?? 'default';

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

export {ClusterLayout};
