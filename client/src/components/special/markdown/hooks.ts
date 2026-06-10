import {useQuery} from '@tanstack/react-query';
import {useMemo} from 'react';
import {LibraryRootFolder} from '../../../@types/library.ts';
import {libraryTreeQueryOptions, useTools} from '../../../queries';
import {routeingPaths} from '../../../routing/paths.ts';
import {useLibraryTreePlainList} from '../../library/model/hooks.ts';
import {useUiHiddenObjects} from '../../../stores/preferences/named-preferences/ui-hidden-objects.ts';
import {useIsAdministrator} from '../../../stores/users/hooks.ts';
import {LibraryTreePlainListBuildOptions} from '../../library/model/tree.ts';

export type MarkdownLink = {
  id: number;
  type: string;
  displayName: string;
  url: string;
};

export function useMarkdownLinks(): MarkdownLink[] {
  const {data: tree = {} as LibraryRootFolder} = useQuery(libraryTreeQueryOptions());
  const {config: hiddenObjects} = useUiHiddenObjects();
  const isAdmin = useIsAdministrator();
  const plainTreeBuildOptions = useMemo<LibraryTreePlainListBuildOptions>(
    () => ({
      includeRootFolder: false,
      includeBackItem: false,
      projectIds: [],
      pipelineVersions: [],
      hiddenObjects,
      checkHiddenObjects: !isAdmin,
    }),
    [hiddenObjects, isAdmin],
  );
  const tools = useTools();
  const flat = useLibraryTreePlainList(tree, plainTreeBuildOptions);
  const flatTreeLinks = useMemo<MarkdownLink[]>(
    () =>
      flat
        .filter((o) => o.object !== undefined && o.object.id !== undefined && o.url)
        .map(
          (o) =>
            ({
              id: o.object.id as number,
              displayName: o.object.name as string,
              type: o.type,
              url: o.url as string,
            }) satisfies MarkdownLink,
        ),
    [flat],
  );
  const flatToolLinks = useMemo(
    () =>
      tools.map((t) => ({
        id: t.id,
        displayName: t.image,
        type: 'tool',
        url: routeingPaths.tool(t.id),
      })),
    [tools],
  );
  return useMemo(() => flatToolLinks.concat(flatTreeLinks), [flatToolLinks, flatTreeLinks]);
}
