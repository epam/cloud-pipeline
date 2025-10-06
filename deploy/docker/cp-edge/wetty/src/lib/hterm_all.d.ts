export interface HtermTerminal {
  decorate: (element: HTMLElement) => void;
  setCursorPosition: (x: number, y: number) => void;
  setCursorVisible: (visible: boolean) => void;
  onTerminalReady: () => void;
  installKeyboard: () => void;
  screenSize: { width: number; height: number } | null;
  io: {
    writeUTF16: (data: string) => void;
    push: () => {
      onVTKeystroke: ((str: string) => void) | null;
      sendString: ((str: string) => void) | null;
      onTerminalResize: ((cols: number, rows: number) => void) | null;
    };
  };
  setProfile: (name: string, cb?: () => void) => void;
  prefs_: {
    importFromJson: (prefs: Record<string, unknown>) => void;
    set: (key: string, value: unknown) => void;
    get: (key: string) => unknown;
    reset: (key: string) => void;
  };
  wipeContents: () => void;
}

export const hterm: {
  Terminal: new () => HtermTerminal;
  defaultStorage: Record<string, unknown>;
};

export {};
