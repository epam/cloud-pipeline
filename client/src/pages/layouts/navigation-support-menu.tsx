import type {CSSProperties} from 'react';
import SupportMenu from '../../components/main/navigation/support-menu';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge.tsx';

type NavigationSupportMenuProps = {
  isLibraryActive: boolean;
};

function NavigationSupportMenu({isLibraryActive}: NavigationSupportMenuProps) {
  const containerStyle: CSSProperties = {
    position: 'absolute',
    left: 0,
    bottom: isLibraryActive ? 44 : 10,
    right: 0,
  };

  return (
    <LegacyComponentBridge
      component={SupportMenu}
      componentProps={{
        itemClassName: 'cp-navigation-menu-item',
        containerStyle,
      }}
    />
  );
}

export {NavigationSupportMenu};
