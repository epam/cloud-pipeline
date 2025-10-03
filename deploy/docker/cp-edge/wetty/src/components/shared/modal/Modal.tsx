import React, { useEffect } from 'react';
import cn from 'classnames';
import styles from './Modal.module.css';
import Button from '../button';

interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  children: React.ReactNode;
  size?: 'small' | 'medium' | 'large';
  showCloseButton?: boolean;
}

const Modal: React.FC<ModalProps> = ({
  isOpen,
  onClose,
  title,
  children,
  size = 'medium',
  showCloseButton = true,
}) => {
  useEffect(() => {
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && isOpen) {
        onClose();
      }
    };
    if (isOpen) {
      document.addEventListener('keydown', handleEscape);
      document.body.style.overflow = 'hidden';
    }
    return () => {
      document.removeEventListener('keydown', handleEscape);
      document.body.style.overflow = 'unset';
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const handleOverlayClick = (event: React.MouseEvent<HTMLDivElement>) => {
    if (event.target === event.currentTarget) {
      onClose();
    }
  };

  return (
    <div className={styles.modalOverlay} onClick={handleOverlayClick}>
      <div
        className={cn(
          styles.modalContent,
          size === 'small' && styles.modalSmall,
          size === 'medium' && styles.modalMedium,
          size === 'large' && styles.modalLarge
        )}
      >
        {(title || showCloseButton) && (
          <div className={styles.modalHeader}>
            {title && <h2 className={styles.modalTitle}>{title}</h2>}
            {showCloseButton && (
              <Button
                className={styles.modalCloseButton}
                onClick={onClose}
                variant="ghost"
              >
                ×
              </Button>
            )}
          </div>
        )}
        <div className={styles.modalBody}>
          {children}
        </div>
      </div>
    </div>
  );
};

export default Modal;
