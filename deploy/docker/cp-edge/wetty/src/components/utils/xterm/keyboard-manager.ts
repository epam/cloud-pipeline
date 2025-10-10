import { Terminal } from "@xterm/xterm";

export type KeyboardManagerCallbacks = {
  onSendText: (text: string) => void;
  onFocus: () => void;
}

export class KeyboardManager {
  private terminalElement: HTMLElement | null = null;
  private terminal: Terminal | null = null;
  private callbacks: KeyboardManagerCallbacks | null = null;

  constructor() {}

  initialize(
    terminalElement: HTMLElement,
    terminal: Terminal,
    callbacks: KeyboardManagerCallbacks
  ): void {
    this.terminalElement = terminalElement;
    this.terminal = terminal;
    this.callbacks = callbacks;
    this.terminalElement.addEventListener('mouseup', this.copySelection);
    this.terminalElement.addEventListener('touchend', this.copySelection);
    this.terminalElement.addEventListener('keydown', this.handleKeydown);
    this.terminalElement.addEventListener('contextmenu', this.handleRightClick);
    this.terminalElement.addEventListener('paste', this.handlePaste);
  }

  private copySelection = async (): Promise<void> => {
    if (!this.terminal || !this.terminal.hasSelection()) return;
    const text = this.terminal.getSelection();
    if (!text) return;
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    textarea.style.left = '-9999px';
    document.body.appendChild(textarea);
    textarea.focus();
    textarea.select();
    document.execCommand('copy');
    document.body.removeChild(textarea);
    this.callbacks?.onFocus();
  };

  private handleKeydown = (event: KeyboardEvent): void => {
    // Handle Ctrl+V or Cmd+V for paste
    if ((event.ctrlKey || event.metaKey) && (event.key.toLowerCase() === 'v')) {
      event.preventDefault();
      this.pasteFromClipboard();
    }
  };

  private handleRightClick = (event: MouseEvent): void => {
    event.preventDefault();
    this.pasteFromClipboard();
  };

  private handlePaste = (event: ClipboardEvent): void => {
    event.preventDefault();
    const text = event.clipboardData?.getData('text');
    if (text) {
      this.sendText(text);
    }
  };

  private pasteFromClipboard = async (): Promise<void> => {
    try {
      const text = await navigator.clipboard.readText();
      if (text) {
        this.sendText(text);
      }
      this.callbacks?.onFocus();
    } catch {
      this.callbacks?.onFocus();
    }
  };

  private sendText = (text: string): void => {
    const normalizedText = text.replace(/\r?\n/g, '\r');
    this.callbacks?.onSendText(normalizedText);
  };

  dispose = (): void => {
    if (this.terminalElement) {
      this.terminalElement.removeEventListener('mouseup', this.copySelection);
      this.terminalElement.removeEventListener('touchend', this.copySelection);
      this.terminalElement.removeEventListener('keydown', this.handleKeydown);
      this.terminalElement.removeEventListener('contextmenu', this.handleRightClick);
      this.terminalElement.removeEventListener('paste', this.handlePaste);
    }
    this.terminalElement = null;
    this.terminal = null;
    this.callbacks = null;
  };
}