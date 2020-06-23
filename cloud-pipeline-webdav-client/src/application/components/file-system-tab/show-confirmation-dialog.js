import {Modal} from 'antd';

export default function showConfirmationDialog (title) {
  return new Promise((resolve) => {
    const onConfirm = () => resolve(true);
    const onCancel = () => resolve(false);
    Modal.confirm({
      title,
      okText: 'OK',
      cancelText: 'CANCEL',
      onOk: onConfirm,
      onCancel: onCancel
    });
  });
}
