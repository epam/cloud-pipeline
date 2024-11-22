import { createHashRouter } from 'react-router-dom';
import {
  UuiContext,
  useUuiServices,
  Router6AdaptedRouter,
} from '@epam/uui-core';
import type { CommonParentProps } from '../../lib/components/common.types.ts';
import '@epam/loveship/styles.css';
import { useLayoutEffect } from 'react';

function Empty() {
  return null;
}

const hashRouter = createHashRouter([{ path: '*', element: <Empty /> }]);
const router = new Router6AdaptedRouter(hashRouter);

export default function Application(props: CommonParentProps) {
  const { className, style, children } = props;
  const { services } = useUuiServices({ router });
  useLayoutEffect(() => {
    document.body.classList.add('uui-theme-loveship');
  }, []);
  return (
    <UuiContext.Provider value={services}>
      <div className={className} style={style}>
        {children}
      </div>
    </UuiContext.Provider>
  );
}
