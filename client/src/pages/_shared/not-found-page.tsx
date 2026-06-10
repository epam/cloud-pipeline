import {Link, useLocation} from 'react-router-dom';
import {routeingPaths as paths} from '../../routing/paths.ts';

function NotFoundPage() {
  const location = useLocation();

  return (
    <div className="flex h-full flex-col items-center justify-center gap-4 p-6 text-center">
      <h1 className="text-2xl font-semibold">Page not found</h1>
      <p className="max-w-md text-sm text-neutral-500">
        No route matches <code className="rounded bg-neutral-100 px-1">{location.pathname}</code>
      </p>
      <Link className="text-blue-600 hover:underline" to={paths.library}>
        Go to Library
      </Link>
    </div>
  );
}

export {NotFoundPage};
