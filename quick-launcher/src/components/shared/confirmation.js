import React, {useCallback, useState} from 'react';
import ReactDOM from 'react-dom';
import Modal from "./modal";
import Markdown from './markdown';
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
      <div>
        {body}
      </div>
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

let index = 0;

export default function confirmation(options = {}) {
  const {
    title,
    message: body,
    md,
    ok = 'Proceed',
    cancel = 'Cancel'
  } = options;
  index += 1;
  return new Promise((resolve) => {
    ReactDOM.render(
      <ConfirmationModal
        key={`confirmation-modal-${index}`}
        title={title}
        body={md ? (<Markdown>{md}</Markdown>) : body}
        onOk={() => resolve(true)}
        onCancel={() => resolve(false)}
        ok={ok}
        cancel={cancel}
      />,
      container
    )
  });
}
