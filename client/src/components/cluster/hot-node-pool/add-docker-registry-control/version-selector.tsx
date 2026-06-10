import {Select} from 'antd';
import type {AddDockerRegistryControlController} from './types.ts';

interface VersionSelectorProps {
  ctrl: AddDockerRegistryControlController;
}

function VersionSelector({ctrl}: VersionSelectorProps) {
  if (ctrl.versions.length === 0) {
    return null;
  }

  const filteredVersions = ctrl.versions.filter((item) => {
    if (item.toLowerCase() === (ctrl.version ?? '').toLowerCase()) {
      return true;
    }

    if (ctrl.dockerImageVersionField && ctrl.dockerImageVersionField.length) {
      return (
        item === ctrl.version ||
        item.toLowerCase().includes(ctrl.dockerImageVersionField.toLowerCase())
      );
    }

    return false;
  });

  return (
    <>
      <span style={{marginLeft: 5, fontWeight: 'bold'}}>Version:</span>
      <Select
        disabled={
          ctrl.pending || ctrl.disabled || (ctrl.versions.length === 1 && Boolean(ctrl.version))
        }
        value={ctrl.version}
        onChange={ctrl.onChangeDockerVersion}
        onSearch={ctrl.setDockerImageVersionField}
        onFocus={() => ctrl.setDockerImageVersionField(undefined)}
        style={{width: 200, marginLeft: 5}}
        getPopupContainer={(node) => node.parentNode as HTMLElement}
        showSearch
        filterOption={false}
        notFoundContent={
          !ctrl.dockerImageVersionField ? 'Start typing to filter versions...' : 'Not found'
        }
      >
        {filteredVersions.map((item) => (
          <Select.Option key={item} value={item}>
            {item}
          </Select.Option>
        ))}
      </Select>
    </>
  );
}

export {VersionSelector};
