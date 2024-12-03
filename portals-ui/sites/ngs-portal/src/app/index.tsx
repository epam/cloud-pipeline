import { RouterProvider } from 'react-router-dom';
import {
  UuiContext,
  useUuiServices,
  Router6AdaptedRouter,
} from '@epam/uui-core';
import { Modals, Snackbar } from '@epam/uui-components';
import '@epam/uui/styles.css';
import '@epam/loveship/styles.css';
import { appRouter } from './config';

const adapterRouter = new Router6AdaptedRouter(appRouter);

export default function Application() {
  const { services } = useUuiServices({ router: adapterRouter });
  return (
    <UuiContext.Provider value={services}>
      <RouterProvider router={appRouter} />
      <Snackbar />
      <Modals />
    </UuiContext.Provider>
  );
}
