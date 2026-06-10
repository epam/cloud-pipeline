import type {ComponentType} from 'react';
import {useLocation} from 'react-router-dom';
import ToolScanningInfo from '../../components/tools/tool-version/scanning-info';
import ToolSettings from '../../components/tools/tool-version/settings';
import ToolPackages from '../../components/tools/tool-version/packages';
import ToolHistory from '../../components/tools/tool-version/history';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

const sectionComponents: Record<string, ComponentType<Record<string, unknown>>> = {
  scaninfo: ToolScanningInfo,
  settings: ToolSettings,
  packages: ToolPackages,
  history: ToolHistory,
};

function ToolVersionSectionPage() {
  const {pathname} = useLocation();
  const sectionKey = pathname.split('/').pop();
  const Component = sectionKey ? sectionComponents[sectionKey] : undefined;

  if (!Component) {
    return null;
  }

  return <LegacyComponentBridge component={Component as never} />;
}

export {ToolVersionSectionPage};
