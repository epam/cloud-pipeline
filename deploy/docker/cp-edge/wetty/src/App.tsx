import { useCallback, useEffect, useState } from 'react'
import './App.css'
import Modal from './components/shared/modal/Modal'
import ThemeManager from './components/theme-manager'
import { initializeTerminal } from './components/utils'
import type { Terminal } from './components/utils/terminal'
import { SettingsIcon } from './components/shared/icons'


function SettingsButton ({ onClick }: { onClick: () => void }) {
  return <div style={{
    position: 'absolute',
    top: 5,
    right: 20,
    width: 20,
    height: 20,
    cursor: 'pointer',
    padding: 2,
    background: 'var(--color-text-inverse)',
    color: 'var(--color-text-primary)',
    borderRadius: 'var(--border-radius)',
    opacity: '0.8',
  }} onClick={onClick}>
    <SettingsIcon /></div>
}

function App() {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [terminal, setTerminal] = useState<Terminal | undefined>(undefined);
  const openModal = useCallback(() => setIsModalOpen(true), []);
  const onModalClose = useCallback(() => {
    setIsModalOpen(false);
    terminal?.focusTerminal();
  }, [terminal]);
  useEffect(() => {
    async function init() {
      const terminal = await initializeTerminal();
      setTerminal(terminal);
    }
    init();
  }, []);

  return (
    <>
      <div
        id="terminal"
        style={{position: 'relative', width: '100%', height: '100%'}}
      />
      <SettingsButton onClick={openModal} />
      <Modal
        isOpen={isModalOpen}
        onClose={onModalClose}
        title="Theme Settings"
        size="medium"
      >
        <ThemeManager
          onCancel={onModalClose}
          terminal={terminal}
        />
      </Modal>
    </>
  )
}

export default App
