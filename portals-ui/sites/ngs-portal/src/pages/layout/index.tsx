import { Outlet } from 'react-router';
import { LinkButton } from '@epam/uui';
import Initialization from '../initialization';
import './style.css';

export default function Layout() {
  return (
    <Initialization>
      <div className="app-layout">
        <header>
          <LinkButton link={{ pathname: '/' }} caption="Home" />
          <LinkButton link={{ pathname: '/projects' }} caption="Projects" />
          <LinkButton link={{ pathname: '/pipelines' }} caption="Pipelines" />
          <LinkButton link={{ pathname: '/runs' }} caption="Runs" />
        </header>
        <main>
          <Outlet />
        </main>
        <footer>Footer</footer>
      </div>
    </Initialization>
  );
}
