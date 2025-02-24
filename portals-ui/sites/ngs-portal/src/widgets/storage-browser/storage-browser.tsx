import classNames from 'classnames';
import { StorageActions, StorageContents, StoragePath } from './components';
import type { CommonProps } from '@cloud-pipeline/components';
import type { StorageContextProps } from './context/provider';
import { StorageContextProvider } from './context/provider';

type Props = CommonProps &
  StorageContextProps & {
    showHeaderControls?: boolean;
    showItemActions?: boolean;
    showBreadcrumbs?: boolean;
    pending?: boolean;
  };

export function StorageBrowser({
  className,
  style,
  showHeaderControls = true,
  showItemActions = true,
  showBreadcrumbs = true,
  pending: pendingProp,
  ...rest
}: Props) {
  return (
    <div className={classNames(className, 'inline-flex', 'flex-col', 'gap-2', 'overflow-hidden')} style={style}>
      <StorageContextProvider {...rest}>
        {(showHeaderControls || showBreadcrumbs) && (
          <div className="flex-shrink-0 flex items-center gap-2">
            {showBreadcrumbs ? <StoragePath className="flex-1" /> : <div className="flex-1" />}
            {showHeaderControls && <StorageActions />}
          </div>
        )}
        <StorageContents pending={pendingProp} showItemActions={showItemActions} />
      </StorageContextProvider>
    </div>
  );
}
