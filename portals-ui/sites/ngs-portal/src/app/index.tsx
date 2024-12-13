import { RouterProvider } from 'react-router-dom';
import {
  UuiContext,
  useUuiServices,
  Router6AdaptedRouter,
} from '@epam/uui-core';
import '@epam/uui/styles.css';
import '@epam/loveship/styles.css';
import { appRouter } from './config';
import { ConfigProvider } from 'antd';
import theme from './theme';

const adapterRouter = new Router6AdaptedRouter(appRouter);

export default function Application() {
  const { services } = useUuiServices({ router: adapterRouter });
  return (
    <ConfigProvider theme={theme}>
      <UuiContext.Provider value={services}>
        <RouterProvider router={appRouter} />
      </UuiContext.Provider>
    </ConfigProvider>
  );
}
