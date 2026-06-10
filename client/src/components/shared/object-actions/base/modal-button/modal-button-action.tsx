import {CommonProps} from '../../../../../@types/common.ts';
import {
  MouseEvent,
  KeyboardEvent,
  FunctionComponent,
  ReactNode,
  useCallback,
  useState,
} from 'react';
import {Button, ButtonProps} from 'antd';

export type ActionButtonProps<Props extends object> = CommonProps &
  Props & {
    id?: string;
    disabled?: boolean;
    size?: ButtonProps['size'];
    children?: ReactNode;
  };

export type ActionModalBaseProps = CommonProps & {
  disabled?: boolean;
  open: boolean;
  onClose?: (event: MouseEvent | KeyboardEvent) => void;
};

export function createActionButtonForModal<Props extends ActionModalBaseProps>(
  ModalComponent: FunctionComponent<Props>,
  children: ReactNode,
  buttonProps?: ButtonProps,
): FunctionComponent<ActionButtonProps<Omit<Props, keyof ActionModalBaseProps>>> {
  const {size: defaultSize} = buttonProps ?? {};
  return (props: ActionButtonProps<Omit<Props, keyof ActionModalBaseProps>>) => {
    const {
      id,
      disabled,
      size = defaultSize,
      className,
      style,
      children: overriddenChildren,
      ...restProps
    } = props;
    const [open, onOpenChange] = useState(false);
    const onClose = useCallback(
      (event: MouseEvent | KeyboardEvent) => {
        if (event) {
          event.stopPropagation();
          event.preventDefault();
        }
        onOpenChange(false);
      },
      [onOpenChange],
    );
    const onOpen = useCallback(
      (event: MouseEvent) => {
        event.stopPropagation();
        event.preventDefault();
        onOpenChange(true);
      },
      [onOpenChange],
    );
    const modalProps: Props = {
      open,
      onClose,
      disabled,
      ...restProps,
    } as Props;
    return (
      <>
        <Button
          id={id}
          {...buttonProps}
          size={size}
          className={className}
          style={style}
          disabled={disabled}
          onClick={onOpen}
        >
          {overriddenChildren ?? children}
        </Button>
        <ModalComponent {...modalProps} />
      </>
    );
  };
}
