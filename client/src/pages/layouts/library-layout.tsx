import {useMemo} from 'react';
import {Outlet} from 'react-router-dom';
import {Splitter} from 'antd';
import {LibraryTree} from '../../components/library/library-tree';
import {useRoutedLibraryItem} from '../../components/library/model/hooks.ts';
import {useLibraryExpanded} from '../../stores/ui-navigation/hooks.ts';
import type {LibraryLayoutOutletContext} from './library-layout-context.ts';
import {Issues} from '../../components/issues/issues.tsx';
import {Metadata} from '../../components/metadata/metadata.tsx';
import {LayoutPanel} from '../_shared/layout-panel.tsx';
import {LibraryHeader} from '../../components/library/library-header/library-header.tsx';
import {LibraryActions} from '../../components/library/library-actions/library-actions.tsx';
import {useLibraryActionsStore} from '../../components/library/library-actions/library-actions-store.ts';
import {getMetadataEntityRefFromLibraryItemId} from '../../components/library/model/tree.ts';

function LibraryLayout() {
  const [activeItemId, onActiveItemIdChange] = useRoutedLibraryItem();
  const activeItemMetadataEntityRef = useMemo(
    () => getMetadataEntityRefFromLibraryItemId(activeItemId),
    [activeItemId],
  );
  const [libraryExpanded] = useLibraryExpanded();
  const actionsStore = useLibraryActionsStore();

  const {issuesPanel, metadataPanel} = actionsStore;
  const {
    visible: metadataPanelVisible,
    setVisible: setMetadataPanelVisible,
    entity: metadataEntity,
  } = metadataPanel;
  const {
    visible: issuesPanelVisible,
    setVisible: setIssuesPanelVisible,
    entity: issuesEntity,
  } = issuesPanel;

  const outletContext = useMemo<LibraryLayoutOutletContext>(
    () => ({
      actionsStore,
    }),
    [actionsStore],
  );

  const sidePanelsVisible = issuesPanelVisible || metadataPanelVisible;

  if (!libraryExpanded) {
    return (
      <section className="h-full min-w-0 flex-1 overflow-auto">
        <Outlet context={outletContext} />
      </section>
    );
  }

  return (
    <Splitter className="h-full">
      <Splitter.Panel defaultSize={300}>
        <LibraryTree
          className="w-full h-full min-h-0 flex flex-col p-2"
          activeItemId={activeItemId}
          onActiveItemIdChange={onActiveItemIdChange}
        />
      </Splitter.Panel>
      <Splitter.Panel>
        <section className="min-w-0 flex-1 overflow-auto h-full flex flex-col p-2">
          <header className="shrink-0 flex items-start w-full">
            <LibraryHeader className="flex-1" activeItemId={activeItemId} />
            <LibraryActions className="shrink-0" actionsStore={actionsStore} />
          </header>
          <Splitter className="flex-1 overflow-auto w-full" onResize={() => {}}>
            <Splitter.Panel key="content" className="h-full overflow-auto">
              <Outlet context={outletContext} />
            </Splitter.Panel>
            <Splitter.Panel
              className="h-full overflow-auto"
              key="side-panels"
              min={100}
              defaultSize={200}
              size={sidePanelsVisible ? undefined : 0}
            >
              <Splitter className="w-full h-full overflow-auto">
                <Splitter.Panel
                  key="issues"
                  className="h-full overflow-auto"
                  size={issuesPanelVisible ? undefined : 0}
                  min={100}
                >
                  <LayoutPanel title="Issues" onClose={() => setIssuesPanelVisible(false)}>
                    <Issues
                      className="w-full h-full overflow-auto"
                      entity={issuesEntity ?? activeItemMetadataEntityRef}
                    />
                  </LayoutPanel>
                </Splitter.Panel>
                <Splitter.Panel
                  key="metadata"
                  className="h-full overflow-auto"
                  size={metadataPanelVisible ? undefined : 0}
                  min={100}
                >
                  <LayoutPanel title="Attributes" onClose={() => setMetadataPanelVisible(false)}>
                    <Metadata
                      className="w-full h-full overflow-auto"
                      entity={metadataEntity ?? activeItemMetadataEntityRef}
                    />
                  </LayoutPanel>
                </Splitter.Panel>
              </Splitter>
            </Splitter.Panel>
          </Splitter>
        </section>
      </Splitter.Panel>
    </Splitter>
  );
}

export {LibraryLayout};
