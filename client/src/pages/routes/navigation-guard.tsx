import {useEffect} from 'react';
import {useLocation, useNavigate} from 'react-router-dom';
import {
  useHomePath,
  useShouldRedirectFromUnavailablePage,
  useUiNavigationLoaded,
} from '../../stores/ui-navigation';

function NavigationGuard() {
  const loaded = useUiNavigationLoaded();
  const homePath = useHomePath();
  const location = useLocation();
  const navigate = useNavigate();
  const shouldRedirect = useShouldRedirectFromUnavailablePage(location.pathname, homePath);

  useEffect(() => {
    if (!loaded || !shouldRedirect) {
      return;
    }
    navigate(homePath, {replace: true});
  }, [loaded, shouldRedirect, homePath, navigate]);

  return null;
}

export {NavigationGuard};
