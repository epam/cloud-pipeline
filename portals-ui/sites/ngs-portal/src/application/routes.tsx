import type { RouteObject } from 'react-router';
import Home from '../pages/home';
import Layout from '../pages/layout';
import Runs from '../pages/runs';
import Pipelines from '../pages/pipelines';
import Projects from '../pages/projects';

const routes: RouteObject[] = [
  {
    path: '/',
    element: <Layout />,
    children: [
      { path: '', element: <Home /> },
      { path: 'projects', element: <Projects /> },
      { path: 'pipelines', element: <Pipelines /> },
      { path: 'runs', element: <Runs /> },
    ],
  },
];

export default routes;
