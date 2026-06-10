import {Button, message, Modal} from 'antd';
import {ActionModalBaseProps} from '../../base/modal-button/modal-button-action.tsx';
import {useCallback, MouseEvent, KeyboardEvent, useState, ReactNode, useMemo} from 'react';
import {queryOptions, useQuery, useQueryClient} from '@tanstack/react-query';
import {folderKeys, libraryTreeKeys} from '../../../../../queries';
import {DetailQueryKeyFactory, useInvalidateDetailQueryOnOpen} from '../../base/hooks.ts';

import {LibraryParentRef} from '../../../../../@types/library.ts';
import {preventDefaultAndStopPropagation} from '../../../../../utilities/callbacks.ts';
import {getErrorDescription} from '../../../../../utilities/errors.ts';
import {LoadingMessage} from '../../../loading-message/loading-message.tsx';
import {UserInfo} from '../../../../../@types/users.ts';
import {useAuthenticatedUser} from '../../../../../stores/users/hooks.ts';
import classNames from 'classnames';

type RemovableObject = {id: number; name: string; parent?: LibraryParentRef};

type RemoveObjectModalBaseProps<Object extends RemovableObject> = {
  queryKey: DetailQueryKeyFactory;
  loadFn: (id: number | undefined) => Promise<Object>;
  deleteFn?: (id: number | undefined) => Promise<unknown>;
  unregisterFn?: (id: number | undefined) => Promise<unknown>;
  title?: ReactNode | ((obj: Object) => ReactNode);
  deleteTitle?: ReactNode;
  unregisterTitle?: ReactNode;
  children?: ReactNode | ((obj: Object) => ReactNode);
  canRemove?: boolean | ((user: UserInfo, obj: Object) => boolean);
  canUnregister?: boolean | ((user: UserInfo, obj: Object) => boolean);
};

type RemoveObjectModalProps<Object extends RemovableObject> = ActionModalBaseProps & {
  obj: number | Object;
  onRemove?: (event: MouseEvent | KeyboardEvent, unregistered: boolean) => void;
};

function RemoveObjectModal<Object extends RemovableObject>(
  props: RemoveObjectModalProps<Object> & RemoveObjectModalBaseProps<Object>,
) {
  const {
    className,
    style,
    obj: _obj,
    open,
    onClose,
    disabled,
    canRemove: _canRemove = true,
    unregisterFn: _unregisterFn,
    canUnregister: _canUnregister = _unregisterFn !== undefined,
    onRemove,
    queryKey,
    loadFn,
    deleteFn: _deleteFn,
    deleteTitle = 'Delete',
    unregisterTitle = 'Unregister',
    title = (o: Object) => (
      <span>
        Are you sure you want to delete <b>{o.name}</b>?
      </span>
    ),
    children = <p>This operation cannot be undone.</p>,
  } = props;
  const user = useAuthenticatedUser();
  const queryClient = useQueryClient();
  const isObjectId = typeof _obj === 'number';
  const objectId = isObjectId ? _obj : _obj.id;
  useInvalidateDetailQueryOnOpen(open && isObjectId, queryKey, objectId);
  const {data: fetchedObject, isFetching: pending} = useQuery(
    queryOptions({
      queryKey: queryKey(objectId),
      queryFn: () => loadFn(objectId),
      enabled: open && isObjectId,
    }),
  );
  const anObject = isObjectId ? fetchedObject : _obj;
  const canDelete = useMemo(() => {
    if (typeof _canRemove === 'boolean') {
      return _canRemove;
    }
    if (!anObject) {
      return false;
    }
    return _canRemove(user, anObject);
  }, [user, anObject, _canRemove]);
  const canUnregister = useMemo(() => {
    if (!_unregisterFn) {
      return false;
    }
    if (typeof _canUnregister === 'boolean') {
      return _canUnregister;
    }
    if (!anObject) {
      return false;
    }
    return _canUnregister(user, anObject);
  }, [user, anObject, _canUnregister, _unregisterFn]);
  const showUnregister = !!_unregisterFn && canUnregister;
  const showDelete = !!_deleteFn && canDelete;
  const [deletePending, setDeletePending] = useState(false);

  const {parent} = anObject ?? {};
  const {id: parentId} = parent ?? {};

  const onCloseWrapper = useCallback(
    (event: MouseEvent | KeyboardEvent) => {
      preventDefaultAndStopPropagation(event);
      if (onClose) {
        onClose(event);
      }
    },
    [onClose],
  );

  const remove = useCallback(
    async (unregister: boolean, event: MouseEvent | KeyboardEvent) => {
      if (anObject) {
        preventDefaultAndStopPropagation(event);
        const hide = message.loading(
          <span>
            {unregister ? 'Unregistering' : 'Removing'} <b>{anObject.name}</b>...
          </span>,
        );
        try {
          setDeletePending(true);
          if (unregister) {
            if (!_unregisterFn || !canUnregister) {
              throw new Error('unregister is not allowed');
            }
            await _unregisterFn(objectId);
          } else {
            if (!_deleteFn || !canDelete) {
              throw new Error('delete is not allowed');
            }
            await _deleteFn(objectId);
          }
          await Promise.all([
            queryClient.invalidateQueries({queryKey: libraryTreeKeys.all}),
            parentId
              ? queryClient.invalidateQueries({queryKey: folderKeys.detail(parentId)})
              : Promise.resolve(),
          ]);
          onRemove?.(event, unregister);
          onCloseWrapper(event);
        } catch (error) {
          message.error(
            <span>
              Error {unregister ? 'unregistering' : 'removing'} <b>{anObject.name}</b>:{' '}
              {getErrorDescription(error)}
            </span>,
          );
          console.error(
            `Failed to ${unregister ? 'unregister' : 'delete'} ${anObject.name} (#${anObject.id}):`,
            error,
          );
        } finally {
          hide();
          setDeletePending(false);
        }
      }
    },
    [
      anObject,
      objectId,
      parentId,
      onRemove,
      queryClient,
      _deleteFn,
      _unregisterFn,
      canUnregister,
      canDelete,
      onCloseWrapper,
    ],
  );

  const onRemoveWrapper = useCallback(
    async (event: MouseEvent | KeyboardEvent) => {
      await remove(false, event);
    },
    [remove],
  );

  const onUnregisterWrapper = useCallback(
    async (event: MouseEvent | KeyboardEvent) => {
      await remove(true, event);
    },
    [remove],
  );

  const singleActionButton = showUnregister !== showDelete;

  return (
    <Modal
      className={className}
      style={style}
      open={open}
      onCancel={onCloseWrapper}
      title={typeof title === 'function' ? (anObject ? title(anObject) : null) : title}
      mask={{
        closable: !deletePending && !disabled,
      }}
      footer={
        <div className="flex items-center">
          <div
            className={classNames('inline-flex', {
              'ml-auto': singleActionButton,
            })}
          >
            <Button
              className="remove-object-modal-cancel"
              onClick={onCloseWrapper}
              disabled={deletePending || pending || disabled}
            >
              Cancel
            </Button>
          </div>
          {(showUnregister || showDelete) && (
            <div
              className={classNames('inline-flex gap-2', {
                'ml-auto': !singleActionButton,
              })}
            >
              {showUnregister && (
                <Button
                  className="remove-object-modal-confirm-unregister"
                  disabled={deletePending || pending || disabled}
                  danger
                  onClick={onUnregisterWrapper}
                >
                  {unregisterTitle}
                </Button>
              )}
              {showDelete && (
                <Button
                  className="remove-object-modal-confirm"
                  disabled={deletePending || pending || disabled}
                  type="primary"
                  danger
                  onClick={onRemoveWrapper}
                >
                  {deleteTitle}
                </Button>
              )}
            </div>
          )}
        </div>
      }
    >
      {typeof children === 'function' ? (
        anObject ? (
          children(anObject)
        ) : (
          <LoadingMessage>Loading...</LoadingMessage>
        )
      ) : (
        children
      )}
    </Modal>
  );
}

export {RemoveObjectModal};
export type {RemovableObject, RemoveObjectModalBaseProps, RemoveObjectModalProps};
