import React, {useCallback, useState} from 'react';
import ReactDOM from 'react-dom';
import Modal from "./modal";
import './confirmation.css';

const container = document.getElementById('modals');

function ConfirmationModal(
  {
    title,
    body,
    onOk,
    onCancel,
    ok,
    cancel
  }
) {
  const [visible, setVisible] = useState(true);
  const onDecision = useCallback(decision => {
    setVisible(false);
    if (decision && onOk) {
      onOk();
    } else if (!decision && onCancel) {
      onCancel();
    }
  }, [setVisible, onOk, onCancel]);
  return (
    <Modal
      closable={false}
      title={title}
      onClose={() => onDecision(false)}
      visible={visible}
      className="confirmation-window"
    >
      <p>
        {body}
      </p>
      <div className="confirmation-window-actions">
        <div
          className="confirmation-window-action"
          onClick={() => onDecision(false)}
        >
          {cancel || 'Cancel'}
        </div>
        <div
          className="confirmation-window-action primary"
          onClick={() => onDecision(true)}
        >
          {ok || 'Confirm'}
        </div>
      </div>
    </Modal>
  );
}

export default function confirmation(options = {}) {
  const {
    title = 'Warning',
    message: body,
    ok = 'Proceed',
    cancel = 'Cancel'
  } = options;
  return new Promise((resolve) => {
    ReactDOM.render(
      <ConfirmationModal
        title={title}
        body={body}
        onOk={() => resolve(true)}
        onCancel={() => resolve(false)}
        ok={ok}
        cancel={cancel}
      />,
      container
    )
  });
}
