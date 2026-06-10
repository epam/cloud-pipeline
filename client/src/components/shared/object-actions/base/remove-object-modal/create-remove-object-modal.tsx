import {
  RemovableObject,
  RemoveObjectModal,
  RemoveObjectModalBaseProps,
  RemoveObjectModalProps,
} from './remove-object-modal.tsx';
import {FunctionComponent} from 'react';

export function createRemoveObjectModal<Object extends RemovableObject, Key extends string>(
  options: RemoveObjectModalBaseProps<Object> & {
    objectProp: Key;
  },
): FunctionComponent<Omit<RemoveObjectModalProps<Object>, 'obj'> & Record<Key, Object | number>> {
  const {objectProp, ...rest} = options;
  return (props: Omit<RemoveObjectModalProps<Object>, 'obj'> & Record<Key, Object | number>) => {
    return <RemoveObjectModal {...rest} {...props} obj={props[objectProp]} />;
  };
}
