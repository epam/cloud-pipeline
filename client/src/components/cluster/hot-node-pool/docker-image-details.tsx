import {useMemo} from 'react';
import {useQuery} from '@tanstack/react-query';
import {RightOutlined} from '@ant-design/icons';
import classNames from 'classnames';
import ToolImage from '../../../models/tools/ToolImage';
import {dockerRegistryQueryOptions} from '../../../queries';
import styles from './docker-image-details.module.css';

interface DockerImageDetailsProps {
  className?: string;
  docker?: string;
  onlyImage?: boolean;
  alignImages?: boolean;
}

function DockerImageDetails({
  className,
  docker,
  onlyImage = false,
  alignImages = false,
}: DockerImageDetailsProps) {
  const {data: {registries = []} = {registries: []}} = useQuery(dockerRegistryQueryOptions());

  const resolved = useMemo(() => {
    if (!docker) {
      return null;
    }

    const [registryPath, groupName, imageWithVersion] = docker.split('/');
    const [imageName, version] = imageWithVersion.split(':');
    let registryLabel = registryPath;
    let toolId: number | undefined;
    let iconId: number | undefined;

    const registry = registries.find(
      (item) => (item.path || '').toLowerCase() === registryPath.toLowerCase(),
    );
    if (registry) {
      registryLabel = registry.description || registry.path;
      const group = (registry.groups || []).find(
        (item) => (item.name || '').toLowerCase() === groupName.toLowerCase(),
      );
      if (group) {
        const tool = (group.tools || []).find(
          (item) => (item.image || '').toLowerCase() === `${groupName}/${imageName}`.toLowerCase(),
        );
        if (tool?.hasIcon && tool.iconId) {
          toolId = tool.id;
          iconId = tool.iconId;
        }
      }
    }

    return {
      registryLabel,
      groupName,
      imageName,
      version,
      toolId,
      iconId,
    };
  }, [docker, registries]);

  if (!docker || !resolved) {
    return null;
  }

  const {registryLabel, groupName, imageName, version, toolId, iconId} = resolved;

  return (
    <div key={docker} className={classNames(styles.container, className)}>
      <span className={classNames('cp-text-not-important', {[styles.hidden]: onlyImage})}>
        {registryLabel}
      </span>
      <RightOutlined
        className={classNames('cp-text-not-important', {[styles.hidden]: onlyImage})}
      />
      <span className={classNames('cp-text-not-important', {[styles.hidden]: onlyImage})}>
        {groupName}
      </span>
      <RightOutlined
        className={classNames('cp-text-not-important', {[styles.hidden]: onlyImage})}
      />
      {toolId && iconId && <img src={ToolImage.url(toolId, iconId)} alt="" />}
      <span
        className={classNames('cp-text', 'cp-text-not-important-after', styles.main, {
          [styles.aligned]: (!toolId || !iconId) && alignImages,
        })}
      >
        {imageName}
      </span>
      {version && version !== 'latest' && (
        <span className="cp-text-not-important">{version}</span>
      )}
    </div>
  );
}

export {DockerImageDetails};
export default DockerImageDetails;
