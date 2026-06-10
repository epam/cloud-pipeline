import {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import type {InputRef} from 'antd';
import {useQuery} from '@tanstack/react-query';
import {preferenceQueryOptions} from '../../../../../queries/preferences/preferences.ts';
import {useStringPreferenceValue} from '../../../../../queries/preferences/hooks.ts';
import type {
  DataStoragePathInputProps,
  FileShareMountListItem,
  StoragePathFormValue,
  StoragePathInputState,
} from './types.ts';
import {extractFileShareMountList, parseFSMountPath, storagePathChanged} from './utils.ts';

function parseValue(
  value: StoragePathFormValue | undefined,
  isFS: boolean,
  fileShareMountsList: FileShareMountListItem[],
): StoragePathInputState {
  if (value) {
    if (isFS) {
      return parseFSMountPath(value, fileShareMountsList);
    }
    return {
      fileShareMountId: value.fileShareMountId,
      regionId: value.regionId,
      storagePath: value.path,
      path: value.path,
    };
  }

  return {
    fileShareMountId: fileShareMountsList.length > 0 ? fileShareMountsList[0].id : undefined,
    regionId: undefined,
    storagePath: undefined,
    path: undefined,
  };
}

function buildPathValue(
  nextState: StoragePathInputState,
  isFS: boolean,
  fileShareMountsList: FileShareMountListItem[],
): StoragePathFormValue {
  const activeMount = nextState.fileShareMountId
    ? fileShareMountsList.find((r) => r.id === nextState.fileShareMountId)
    : undefined;

  if (isFS && activeMount) {
    return {
      fileShareMountId: activeMount.id,
      regionId: activeMount.regionId,
      path: `${activeMount.mountRoot}${activeMount.separator}${nextState.storagePath || ''}`,
    };
  }

  return {
    fileShareMountId: undefined,
    regionId: undefined,
    path: nextState.storagePath,
  };
}

function validateFsPath(
  nextState: StoragePathInputState,
  fileShareMountsList: FileShareMountListItem[],
  onValidation?: (valid: boolean) => void,
) {
  const activeMount = nextState.fileShareMountId
    ? fileShareMountsList.find((r) => r.id === nextState.fileShareMountId)
    : undefined;

  if (!activeMount) {
    onValidation?.(false);
    return;
  }

  const valid = !!nextState.storagePath && nextState.storagePath.startsWith('/');
  onValidation?.(valid);
}

export function useDataStoragePathInput({
  value,
  onChange,
  isFS = false,
  visible,
  onValidation,
  cloudRegions,
}: Pick<
  DataStoragePathInputProps,
  'value' | 'onChange' | 'isFS' | 'visible' | 'onValidation' | 'cloudRegions'
>) {
  const [state, setState] = useState<StoragePathInputState>({});

  const fileShareMountsList = useMemo(
    () => extractFileShareMountList(cloudRegions),
    [cloudRegions],
  );

  const currentFileShareMount = useMemo(
    () =>
      state.fileShareMountId
        ? fileShareMountsList.find((r) => r.id === state.fileShareMountId)
        : undefined,
    [fileShareMountsList, state.fileShareMountId],
  );

  const {isPending: preferencePending} = useQuery(preferenceQueryOptions('storage.object.prefix'));
  const storageObjectPrefix = useStringPreferenceValue('storage.object.prefix');

  const nameInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    setState((currentState) => {
      if (storagePathChanged(value, currentState)) {
        return parseValue(value, isFS, fileShareMountsList);
      }
      return currentState;
    });
  }, [value, isFS, fileShareMountsList]);

  useEffect(() => {
    if (!visible) {
      setState((prev) => ({...prev, fileShareMountId: null}));
      return;
    }
    if (nameInputRef.current) {
      const timer = window.setTimeout(() => nameInputRef.current?.focus(), 0);
      return () => window.clearTimeout(timer);
    }
  }, [visible]);

  const emitChange = useCallback(
    (nextState: StoragePathInputState) => {
      const activeMount = nextState.fileShareMountId
        ? fileShareMountsList.find((r) => r.id === nextState.fileShareMountId)
        : undefined;

      if (!onChange || (isFS && !activeMount)) {
        return;
      }

      onChange(buildPathValue(nextState, isFS, fileShareMountsList));
      validateFsPath(nextState, fileShareMountsList, onValidation);
    },
    [fileShareMountsList, isFS, onChange, onValidation],
  );

  const validatePath = useCallback(() => {
    validateFsPath(state, fileShareMountsList, onValidation);
  }, [fileShareMountsList, onValidation, state]);

  const onSelectFileShareMount = useCallback(
    (fileShareMount: FileShareMountListItem) => {
      if (!state.fileShareMountId || state.fileShareMountId !== fileShareMount.id) {
        const nextState = {...state, fileShareMountId: fileShareMount.id};
        setState(nextState);
        emitChange(nextState);
      } else {
        emitChange(state);
      }
    },
    [emitChange, state],
  );

  const onPathChanged = useCallback(
    (storagePath: string) => {
      const nextState = {...state, storagePath};
      setState(nextState);
      emitChange(nextState);
    },
    [emitChange, state],
  );

  const initializeNameInput = useCallback(
    (inputRef: InputRef | null) => {
      const input = inputRef?.input ?? null;
      if (!input) {
        return;
      }
      nameInputRef.current = input;
      input.onfocus = function (this: HTMLInputElement) {
        window.setTimeout(() => {
          this.selectionStart = (this.value || '').length;
          this.selectionEnd = (this.value || '').length;
        }, 0);
      };
      if (visible) {
        window.setTimeout(() => input.focus(), 0);
      }
    },
    [visible],
  );

  return {
    state,
    fileShareMountsList,
    currentFileShareMount,
    preferencePending,
    storageObjectPrefix,
    validatePath,
    onSelectFileShareMount,
    onPathChanged,
    initializeNameInput,
  };
}
