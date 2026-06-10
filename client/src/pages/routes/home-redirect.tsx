import {Navigate} from 'react-router-dom';
import {useHomePath, useUiNavigationLoaded} from '../../stores/ui-navigation';
import {LoadingMessage} from '../../components/shared/loading-message/loading-message.tsx';

function HomeRedirect() {
  const loaded = useUiNavigationLoaded();
  const home = useHomePath();

  if (!loaded) {
    return (
      <div className="flex h-full items-center justify-center">
        <LoadingMessage>Loading...</LoadingMessage>
      </div>
    );
  }

  if (/^https?:\/\//i.test(home)) {
    window.location.href = home;
    return null;
  }

  return <Navigate replace to={home} />;
}

export {HomeRedirect};
