import {RouterProvider} from 'react-router-dom';
import {routing} from '../../mobx-stores/legacy-stores.js';
import {router} from '../routes';

routing.setRouter(router);

function AppRouter() {
  return <RouterProvider router={router} />;
}

export {AppRouter};
