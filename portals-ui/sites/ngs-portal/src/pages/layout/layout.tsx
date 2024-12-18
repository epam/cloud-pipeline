import { App as AntApp } from 'antd';
import { Outlet } from 'react-router';
import Initialization from '../initialization';
import { Header } from '../../widgets/header';
import './style.css';

export const Layout = () => {
  return (
    <Initialization>
      <AntApp className="app-layout">
        <Header />
        <div className="layout-content">
          <Outlet />
        </div>
      </AntApp>
    </Initialization>
  );
};
