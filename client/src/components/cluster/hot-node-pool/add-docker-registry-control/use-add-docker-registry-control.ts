import {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {loadToolTags} from '../../../../api/tools/tools-api.ts';
import {dockerRegistryQueryOptions, getQueryErrorMessage} from '../../../../queries';
import type {
  AddDockerRegistryControlController,
  AddDockerRegistryControlProps,
  ToolVersionWithId,
} from './types.ts';
import {
  buildToolGroups,
  filterToolGroups,
  findToolByDockerImage,
  getToolId,
  mapToolTagsToVersions,
  pickVersion,
} from './utils.ts';

export function useAddDockerRegistryControl({
  disabled,
  duplicate,
  className,
  docker: dockerProp,
  showError = false,
  showDelete = true,
  style,
  onChange,
  onRemove,
  multipleMode,
  versionsSelected: versionsSelectedProp,
  containerStyle,
  imagesToExclude,
}: AddDockerRegistryControlProps): AddDockerRegistryControlController {
  const [docker, setDocker] = useState<string | undefined>();
  const [version, setVersion] = useState<string | undefined>();
  const [versionsSelected, setVersionsSelected] = useState<ToolVersionWithId[]>([]);
  const [versionsPending, setVersionsPending] = useState(false);
  const [pending, setPending] = useState(false);
  const [versions, setVersions] = useState<string[]>([]);
  const [versionsWithIdentifiers, setVersionsWithIdentifiers] = useState<ToolVersionWithId[]>([]);
  const [dockerImageField, setDockerImageField] = useState<string | undefined>();
  const [dockerImageVersionField, setDockerImageVersionField] = useState<string | undefined>();

  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;
  const versionsSelectedRef = useRef(versionsSelected);
  versionsSelectedRef.current = versionsSelected;
  const versionRef = useRef(version);
  versionRef.current = version;
  const dockerRef = useRef(docker);
  dockerRef.current = docker;

  const {data: {registries = []} = {registries: []}, error} = useQuery(
    dockerRegistryQueryOptions(),
  );
  const errorMessage = getQueryErrorMessage(error);

  const toolGroups = useMemo(() => buildToolGroups(registries), [registries]);
  const filteredToolGroups = useMemo(
    () => filterToolGroups(toolGroups, docker, dockerImageField),
    [toolGroups, docker, dockerImageField],
  );

  const notifyChange = useCallback(
    (
      nextDocker: string | undefined,
      nextVersion: string | undefined,
      nextVersionsSelected: ToolVersionWithId[],
    ) => {
      const change = onChangeRef.current;
      if (!change || !nextDocker) {
        return;
      }

      if (multipleMode) {
        const currentToolId = getToolId(nextDocker, toolGroups);
        (change as (docker: string, versions: ToolVersionWithId[], toolId?: number) => void)(
          nextDocker,
          nextVersionsSelected,
          currentToolId,
        );
        return;
      }

      if (nextVersion) {
        (change as (imageWithVersion: string) => void)(`${nextDocker}:${nextVersion}`);
      }
    },
    [multipleMode, toolGroups],
  );

  useEffect(() => {
    if (multipleMode && dockerProp) {
      setDocker(dockerProp);
      setVersionsSelected(versionsSelectedProp ?? []);
      return;
    }

    if (!multipleMode && dockerProp) {
      const [registryPath, groupName, imageWithVersion] = dockerProp.split('/');
      const [imageName, imageVersion] = imageWithVersion.split(':');
      setDocker([registryPath, groupName, imageName].join('/'));
      setVersion(imageVersion || 'latest');
      return;
    }

    setDocker(undefined);
    setVersion(undefined);
    setVersions([]);
    setVersionsWithIdentifiers([]);
    setVersionsSelected([]);
  }, [dockerProp, multipleMode]);

  useEffect(() => {
    if (!docker) {
      return;
    }

    let cancelled = false;
    setVersionsPending(true);
    setVersions([]);
    setVersionsWithIdentifiers([]);

    async function fetchVersions() {
      const tool = findToolByDockerImage(docker, registries);
      if (!tool) {
        if (!cancelled) {
          setVersionsPending(false);
          setPending(false);
        }
        return;
      }

      try {
        const tags = await loadToolTags(tool.id);
        if (cancelled) {
          return;
        }

        const versionsWithIds = mapToolTagsToVersions(tags);
        const versionsArray = versionsWithIds.map((item) => item.version);
        const nextVersion = pickVersion(versionRef.current, versionsArray);

        setVersions(versionsArray);
        setVersionsWithIdentifiers(versionsWithIds);
        setVersion(nextVersion);
        setVersionsPending(false);
        setPending(false);

        if (multipleMode) {
          notifyChange(docker, nextVersion, versionsSelectedRef.current);
        } else {
          notifyChange(docker, nextVersion, []);
        }
      } catch {
        if (!cancelled) {
          setVersionsPending(false);
          setPending(false);
        }
      }
    }

    fetchVersions().catch(() => {});

    return () => {
      cancelled = true;
    };
  }, [docker, registries, multipleMode, notifyChange]);

  const handleDockerImageChange = useCallback((image: string) => {
    if (dockerRef.current === image) {
      return;
    }

    setDocker(image);
    setVersion('latest');
    setVersionsSelected([]);
    setDockerImageField(undefined);
    setDockerImageVersionField(undefined);
  }, []);

  const onChangeDockerVersion = useCallback(
    (nextVersion: string) => {
      if (versionRef.current === nextVersion) {
        return;
      }

      setDockerImageVersionField(undefined);
      setVersion(nextVersion);
      notifyChange(docker, nextVersion, versionsSelectedRef.current);
    },
    [docker, notifyChange],
  );

  const onChangeMultipleVersions = useCallback(
    (selectedVersions: string[]) => {
      const nextVersionsSelected = (selectedVersions ?? [])
        .map((name) => versionsWithIdentifiers.find((item) => item.version === name))
        .filter((item): item is ToolVersionWithId => Boolean(item));

      setVersionsSelected(nextVersionsSelected);
      notifyChange(docker, versionRef.current, nextVersionsSelected);
    },
    [docker, notifyChange, versionsWithIdentifiers],
  );

  return {
    errorMessage,
    showError,
    disabled,
    duplicate,
    className,
    style,
    containerStyle,
    showDelete,
    multipleMode,
    pending,
    docker,
    dockerImageField,
    filteredToolGroups,
    imagesToExclude,
    version,
    versions,
    versionsPending,
    versionsSelected,
    dockerImageVersionField,
    onChangeDockerImage: handleDockerImageChange,
    onChangeDockerVersion,
    onChangeMultipleVersions,
    setDockerImageField,
    setDockerImageVersionField,
    onRemove,
  };
}
