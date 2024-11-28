import { Outlet } from 'react-router';
import Initialization from '../initialization';
import { Header } from '../../widgets/header';
import './style.css';

export const Layout = () => {
  return (
    <Initialization>
      <div className="app-layout">
        <Header />
        <main>
          <Outlet />
        </main>
      </div>
    </Initialization>
  );
};
