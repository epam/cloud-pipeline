import { createHashRouter } from 'react-router-dom';
import routes from './routes.tsx';

const appRouter = createHashRouter(routes);

export default appRouter;
