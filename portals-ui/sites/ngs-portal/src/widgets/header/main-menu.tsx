import { useLocation } from 'react-router';
import { Link } from 'react-router-dom';
import { AppRoutes, RoutePath } from '../../shared/constants/routes';
import { Menu } from 'antd';
import './style.css';

const LINKS = [
  { route: RoutePath[AppRoutes.HOME], caption: 'Home' },
  { route: RoutePath[AppRoutes.PROJECTS], caption: 'Projects' },
  { route: RoutePath[AppRoutes.PIPELINES], caption: 'Pipelines' },
  { route: RoutePath[AppRoutes.RUNS], caption: 'Runs' },
];

export const MainMenu = () => {
  const location = useLocation();

  return (
    <Menu
      mode="horizontal"
      selectedKeys={[location.pathname]}
      theme="dark"
      className="main-menu text-gray">
      {LINKS.map(({ route, caption }) => (
        <Menu.Item key={route}>
          <Link className="text-white" to={route}>
            {caption}
          </Link>
        </Menu.Item>
      ))}
    </Menu>
  );
};
