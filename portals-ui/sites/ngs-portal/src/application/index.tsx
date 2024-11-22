import { RouterProvider } from 'react-router-dom';
import {
  UuiContext,
  useUuiServices,
  Router6AdaptedRouter,
} from '@epam/uui-core';
import '@epam/uui/styles.css';
import '@epam/loveship/styles.css';
import router from './router';

const adapterRouter = new Router6AdaptedRouter(router);

export default function Application() {
  const { services } = useUuiServices({ router: adapterRouter });
  return (
    <UuiContext.Provider value={services}>
      <RouterProvider router={router} />
    </UuiContext.Provider>
  );
}
