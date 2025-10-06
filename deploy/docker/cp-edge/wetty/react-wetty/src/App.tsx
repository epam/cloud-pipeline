import { useEffect, useState } from 'react'
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
    right: 40,
    width: 20,
    height: 20,
    cursor: 'pointer'
  }} onClick={onClick}>
    <SettingsIcon /></div>
}

function App() {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [terminal, setTerminal] = useState<Terminal | undefined>(undefined);
  const openModal = () => setIsModalOpen(true);
  const closeModal = () => setIsModalOpen(false);
  useEffect(() => {
    async function init() {
      const terminal = await initializeTerminal();
      setTerminal(terminal);
    }
    init();
  }, []);

  return (
    <>
      <div id="settings">
        <svg width="20" height="20" viewBox="0 0 20 20" xmlns="http://www.w3.org/2000/svg">
            <path d="M1 19 L19 1 L19 19 L1 19" fill="white" stroke="#666666"></path>
            <path d="M1 19 L19 1 L1 1 L1 19" fill="black" stroke="#666666"></path>
        </svg>
      </div>
      <div id="terminal" style={{position: 'relative', width: '100%', height: '100%'}} />
      <SettingsButton onClick={openModal} />
      
      <Modal
        isOpen={isModalOpen}
        onClose={closeModal}
        title="Theme Settings"
        size="medium"
      >
        <ThemeManager
          onCancel={closeModal}
          terminal={terminal}
        />
      </Modal>
    </>
  )
}

export default App
