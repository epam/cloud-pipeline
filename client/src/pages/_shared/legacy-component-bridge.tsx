import {useCallback, useMemo, type ComponentType} from 'react';
import {useLocation, useNavigate, useParams} from 'react-router-dom';
import {LegacyMobXStoresProvider} from './legacy-mobx-stores-provider.tsx';

type LegacyRouter = {
  push: ReturnType<typeof useNavigate>;
  replace: (to: string) => void | Promise<void>;
  location: ReturnType<typeof useLocation>;
  params: Record<string, string | undefined>;
};

type LegacyComponentBridgeProps<T extends Record<string, unknown>> = {
  component: ComponentType<
    T & {
      params?: Record<string, string | undefined>;
      router?: LegacyRouter;
      location?: ReturnType<typeof useLocation>;
    }
  >;
  componentProps?: T;
};

/**
 * Renders a legacy class component from src/pages with:
 * - MobX store injection (LegacyMobXStoresProvider)
 * - route params / router / location passed as props (withRouter-compatible shape)
 *
 * Prefer passing route-derived values explicitly via componentProps when possible.
 */
function LegacyComponentBridge<T extends Record<string, unknown>>({
  component: Component,
  componentProps,
}: LegacyComponentBridgeProps<T>) {
  const navigate = useNavigate();
  const params = useParams();
  const location = useLocation();
  const replace = useCallback((to: string) => navigate(to, {replace: true}), [navigate]);
  const router: LegacyRouter = useMemo(
    () => ({
      push: navigate,
      replace,
      location,
      params,
    }),
    [navigate, replace, location, params],
  );

  return (
    <LegacyMobXStoresProvider>
      <Component {...(componentProps as T)} params={params} router={router} location={location} />
    </LegacyMobXStoresProvider>
  );
}

export {LegacyComponentBridge};
export type {LegacyRouter};
