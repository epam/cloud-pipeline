import {useLocation} from 'react-router-dom';
import {SearchDialog} from '../../components/search';
import {navigationPages} from '../../routing/paths.ts';
import {useActiveNavigationKey, useSearchEnabled} from '../../stores/ui-navigation/hooks.ts';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

type MainOverlaysProps = {
  onVisibilityChanged?: (visible: boolean) => void;
};

function MainOverlays({onVisibilityChanged}: MainOverlaysProps) {
  const location = useLocation();
  const searchEnabled = useSearchEnabled();
  const activeKey = useActiveNavigationKey(location.pathname);
  const isAdvancedSearch = /\/search\/advanced/i.test(location.pathname);
  const blockInput = activeKey === navigationPages.run || isAdvancedSearch;

  if (!searchEnabled) {
    return null;
  }

  return (
    <LegacyComponentBridge
      component={SearchDialog}
      componentProps={{
        blockInput,
        onVisibilityChanged,
      }}
    />
  );
}

export {MainOverlays};
