import { Outlet } from 'react-router';
import Initialization from '../initialization';
import './style.css';
import { Header } from '../../widgets/header';

export const Layout = () => {
  return (
    <Initialization>
      <div className="app-layout">
        <Header />
        <main>
          <Outlet />
        </main>
        <footer>Footer</footer>
      </div>
    </Initialization>
  );
};
