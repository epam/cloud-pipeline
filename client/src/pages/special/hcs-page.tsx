import {useMemo} from 'react';
import {useLocation} from 'react-router-dom';
import {HcsImagePage} from '../../components/special/hcs-image';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

function HcsPage() {
  const location = useLocation();
  const legacyLocation = useMemo(
    () => ({
      ...location,
      query: Object.fromEntries(new URLSearchParams(location.search)),
    }),
    [location],
  );

  return (
    <LegacyComponentBridge component={HcsImagePage} componentProps={{location: legacyLocation}} />
  );
}

export {HcsPage};
