import MultiSelect from '../../../special/multiSelect';
import type {AddDockerRegistryControlController} from './types.ts';

interface MultipleVersionsSelectorProps {
  ctrl: AddDockerRegistryControlController;
}

function MultipleVersionsSelector({ctrl}: MultipleVersionsSelectorProps) {
  if (ctrl.versions.length === 0) {
    return null;
  }

  return (
    <div
      style={{
        marginLeft: 'auto',
        display: 'flex',
        flexWrap: 'nowrap',
        alignItems: 'center',
      }}
    >
      <span
        style={{
          marginLeft: 5,
          fontWeight: 'bold',
          verticalAlign: 'center',
        }}
      >
        Versions:
      </span>
      <MultiSelect
        onChange={ctrl.onChangeMultipleVersions}
        values={ctrl.versionsSelected.map((item) => item.version)}
        options={ctrl.versions}
        disabled={ctrl.disabled}
        pending={ctrl.pending || ctrl.versionsPending}
      />
    </div>
  );
}

export {MultipleVersionsSelector};
