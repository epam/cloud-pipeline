import { Link } from 'react-router-dom';
import { Menu } from 'antd';
import { useSelectedMenuItemKeys } from './use-selected-menu-item-keys.ts';
import { mainMenuItems } from './items.ts';
import '../style.css';

const menuItems = mainMenuItems.map((m) => (
  <Menu.Item key={m.key}>
    <Link className="text-white" to={m.uri}>
      {m.caption}
    </Link>
  </Menu.Item>
));

export const MainMenu = () => {
  const selectedKeys = useSelectedMenuItemKeys();

  return (
    <Menu
      mode="horizontal"
      selectedKeys={selectedKeys}
      theme="dark"
      className="main-menu text-gray">
      {menuItems}
    </Menu>
  );
};
