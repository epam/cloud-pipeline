import {ReactNode, useMemo, useState, createElement, Fragment, useCallback, useEffect} from 'react';
import {createPortal} from 'react-dom';
import {MetadataEntityRef} from '../../../@types/metadata.ts';

export type LibraryMenuActionType = 'action' | 'toggle';

export type LibraryMenuActionConfigBase<Type extends LibraryMenuActionType> = {
  key: string;
  title: ReactNode;
  type: Type;
  disabled?: boolean;
  details?: ReactNode;
};

export type LibraryMenuAction = LibraryMenuActionConfigBase<'action'> & {
  handler: () => void;
};

export type LibraryMenuToggle = LibraryMenuActionConfigBase<'toggle'> & {
  checked: boolean;
  handler: (enabled: boolean) => void;
};

export type LibraryMenuDefaultActions = 'issues' | 'metadata' | 'divider';

export type LibraryMenuActions = Array<
  LibraryMenuDefaultActions | LibraryMenuAction | LibraryMenuToggle
>;

export type LibraryFormattedAction = 'divider' | LibraryMenuAction | LibraryMenuToggle;

export type LibraryPanelState = {
  visible: boolean;
  entity?: MetadataEntityRef;
  setVisible(visible: boolean): void;
  openEntity(entity?: MetadataEntityRef): void;
};

export type LibraryActionsStore = {
  issuesPanel: LibraryPanelState;
  metadataPanel: LibraryPanelState;
  actions: LibraryFormattedAction[];
  renderActions(...actions: ReactNode[]): ReactNode;
  renderActionsAfterMenu(...actions: ReactNode[]): ReactNode;
  attachContainer(
    container: HTMLDivElement | undefined | null,
    afterMenuContainer: HTMLDivElement | undefined | null,
  ): void;
  configureMenuActions(actions: LibraryMenuActions): () => void;
};

function useLibraryPanelState(): LibraryPanelState {
  const [visible, setVisible] = useState(false);
  const [entity, setEntity] = useState<MetadataEntityRef | undefined>(undefined);
  const onVisibleChange = useCallback(
    (visible: boolean) => {
      if (!visible) {
        setEntity(undefined);
      }
      setVisible(visible);
    },
    [setVisible, setEntity],
  );
  const openEntity = useCallback(
    (entity: MetadataEntityRef | undefined) => {
      setEntity(entity);
      setVisible(true);
    },
    [setEntity, setVisible],
  );
  return useMemo(
    () => ({
      visible,
      setVisible: onVisibleChange,
      entity,
      openEntity,
    }),
    [visible, onVisibleChange, entity, openEntity],
  );
}

function useLibraryPanelAutoHide(
  availableActions: LibraryFormattedAction[],
  panelKey: string,
  setPanelVisibility: (visible: boolean) => void,
): void {
  const available = availableActions.some((o) => isToggle(o) && o.key === panelKey);
  useEffect(() => {
    if (!available) {
      setPanelVisibility(false);
    }
  }, [available, setPanelVisibility]);
}

export function isDivider(item: LibraryFormattedAction): item is 'divider' {
  return typeof item === 'string' && item === 'divider';
}

export function isAction(item: LibraryFormattedAction): item is LibraryMenuAction {
  return typeof item === 'object' && item.type === 'action';
}

export function isToggle(item: LibraryFormattedAction): item is LibraryMenuToggle {
  return typeof item === 'object' && item.type === 'toggle';
}

export function doAction(item: LibraryFormattedAction) {
  if (isDivider(item)) {
    return;
  }
  if (isAction(item)) {
    item.handler();
  }
  if (isToggle(item)) {
    item.handler(!item.checked);
  }
}

export function useLibraryActionsStore(): LibraryActionsStore {
  const issuesPanel = useLibraryPanelState();
  const metadataPanel = useLibraryPanelState();
  const {visible: issuesVisible, setVisible: setIssuesVisible} = issuesPanel;
  const {visible: metadataVisible, setVisible: setMetadataVisible} = metadataPanel;
  const [actions, setActions] = useState<LibraryFormattedAction[]>([]);
  const [storeContainer, setStoreContainer] = useState<HTMLDivElement | undefined>();
  const [storeContainerAfterMenu, setStoreContainerAfterMenu] = useState<
    HTMLDivElement | undefined
  >();
  const attachContainer = useCallback(
    (container: HTMLDivElement | undefined | null, afterMenuContainer: HTMLDivElement) => {
      setStoreContainer(container ?? undefined);
      setStoreContainerAfterMenu(afterMenuContainer);
    },
    [setStoreContainer, setStoreContainerAfterMenu],
  );
  const renderActions = useCallback(
    (...actions: ReactNode[]): ReactNode => {
      if (storeContainer) {
        return createPortal(createElement(Fragment, {}, ...actions), storeContainer);
      }
      return null;
    },
    [storeContainer],
  );
  const renderActionsAfterMenu = useCallback(
    (...actions: ReactNode[]): ReactNode => {
      if (storeContainerAfterMenu) {
        return createPortal(createElement(Fragment, {}, ...actions), storeContainerAfterMenu);
      }
      return null;
    },
    [storeContainerAfterMenu],
  );
  const configureMenuActions = useCallback(
    (actions: LibraryMenuActions): (() => void) => {
      const result: LibraryFormattedAction[] = [];
      actions.forEach((action) => {
        if (typeof action === 'string') {
          switch (action) {
            case 'issues':
              result.push({
                key: action,
                type: 'toggle',
                checked: issuesVisible,
                title: 'Issues',
                handler: setIssuesVisible,
              });
              break;
            case 'metadata':
              result.push({
                key: action,
                type: 'toggle',
                checked: metadataVisible,
                title: 'Attributes',
                handler: setMetadataVisible,
              });
              break;
            case 'divider':
              result.push(action);
              break;
            default:
              break;
          }
        } else {
          result.push(action);
        }
      });
      setActions(result);
      return () => {
        setActions([]);
      };
    },
    [setActions, issuesVisible, setIssuesVisible, metadataVisible, setMetadataVisible],
  );
  useLibraryPanelAutoHide(actions, 'issues', setIssuesVisible);
  useLibraryPanelAutoHide(actions, 'metadata', setMetadataVisible);
  return useMemo(
    () => ({
      issuesPanel,
      metadataPanel,
      actions,
      renderActions,
      renderActionsAfterMenu,
      attachContainer,
      configureMenuActions,
    }),
    [
      issuesPanel,
      metadataPanel,
      actions,
      renderActions,
      renderActionsAfterMenu,
      attachContainer,
      configureMenuActions,
    ],
  );
}
