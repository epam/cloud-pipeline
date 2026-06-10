import {Select} from 'antd';
import {RightOutlined} from '@ant-design/icons';
import classNames from 'classnames';
import {DockerImageDetails} from '../docker-image-details.tsx';
import type {AddDockerRegistryControlController} from './types.ts';

interface DockerImageSelectProps {
  ctrl: AddDockerRegistryControlController;
}

function DockerImageSelect({ctrl}: DockerImageSelectProps) {
  const notFoundContent = !ctrl.dockerImageField
    ? 'Start typing to filter docker images...'
    : ctrl.dockerImageField.length < 3
      ? 'Start typing to filter docker images...'
      : 'Not found';

  const isOptionDisabled = (dockerImage: string) =>
    Boolean(ctrl.imagesToExclude?.length && ctrl.imagesToExclude.includes(dockerImage));

  return (
    <Select
      className={classNames({'cp-error': ctrl.duplicate})}
      showSearch
      disabled={ctrl.pending || ctrl.disabled}
      value={ctrl.docker}
      onChange={ctrl.onChangeDockerImage}
      onSearch={ctrl.setDockerImageField}
      onFocus={() => ctrl.setDockerImageField(undefined)}
      placeholder="Docker image"
      style={{flex: 1}}
      filterOption={false}
      getPopupContainer={(node) => node.parentNode as HTMLElement}
      notFoundContent={notFoundContent}
    >
      {ctrl.filteredToolGroups.map((group) => (
        <Select.OptGroup
          key={group.key}
          label={
            <span>
              {group.registryLabel}
              <RightOutlined />
              {group.groupName}
            </span>
          }
        >
          {group.tools.map((tool) => {
            const [registryPath, groupName, imagePath] = tool.dockerImage.split('/');
            const registry = tool.registry.description || registryPath;
            const title = `${registry} > ${groupName} > ${imagePath}`;

            return (
              <Select.Option
                key={tool.id}
                value={tool.dockerImage}
                style={{
                  background: isOptionDisabled(tool.dockerImage) ? '#dfdfdf' : 'none',
                }}
                disabled={isOptionDisabled(tool.dockerImage)}
                title={title}
              >
                <DockerImageDetails docker={tool.dockerImage} />
              </Select.Option>
            );
          })}
        </Select.OptGroup>
      ))}
    </Select>
  );
}

export {DockerImageSelect};
