import { Link } from 'react-router-dom';
import { Menu } from 'antd';
import { useSelectedMenuItemKeys } from './use-selected-menu-item-keys.ts';
import { mainMenuItems } from './items.ts';
import '../style.css';

const menuItems = mainMenuItems.map((m) => ({
  key: m.key,
  label: (
    <Link key={m.key} className="text-white" to={m.uri}>
      {m.caption}
    </Link>
  ),
}));

export const MainMenu = () => {
  const selectedKeys = useSelectedMenuItemKeys();

  return (
    <Menu
      mode="horizontal"
      selectedKeys={selectedKeys}
      theme="dark"
      items={menuItems}
      className="main-menu text-gray"
    />
  );
};
