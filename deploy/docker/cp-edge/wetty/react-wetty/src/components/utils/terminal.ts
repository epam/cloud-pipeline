import {
  ThemeName,
  SocketEvent,
  type HtermTerminal,
  type SocketLike,
  type TerminalTheme,
} from "./types";
import { hterm } from "../../lib/hterm_all.js";

export class Terminal {
  private static instance: Terminal | null = null;
  private socket: SocketLike | null = null;
  private term: HtermTerminal | null = null;
  private buffer: string = "";
  private currentTheme: string = ThemeName.DEFAULT;
  private origin: string;
  private isConnected: boolean = false;

  constructor(origin: string = "http://localhost:3030") {
    this.origin = origin;
  }

  static getInstance(): Terminal | null {
    return Terminal.instance;
  }

  static createInstance(origin?: string): Terminal {
    if (!Terminal.instance) {
      Terminal.instance = new Terminal(origin);
    }
    return Terminal.instance;
  }

  async initialize(): Promise<void> {
    return new Promise((resolve, reject) => {
      try {
        if (!window?.io) {
          reject(new Error("socket.io not loaded"));
        }
        this.socket = window.io(this.origin, { transports: ["websocket"] });
        this.socket.on(SocketEvent.CONNECT, () => {
          this.isConnected = true;
          this.initializeHterm().then(resolve).catch(reject);
        });
        this.socket.on(SocketEvent.OUTPUT, (data?: unknown) => {
          if (typeof data === "string") {
            this.receive(data);
          }
        });
        this.socket.on(SocketEvent.DISCONNECT, () => {
          this.isConnected = false;
          console.log("Socket.io connection closed");
        });
        this.socket.on(SocketEvent.THEME, (sshTheme: unknown) => {
          if (typeof sshTheme === "string") {
            this.setTheme(sshTheme);
          }
        });
      } catch (error) {
        reject(error);
      }
    });
  }

  private async initializeHterm(): Promise<void> {
    return new Promise((resolve, reject) => {
      try {
        this.term = new hterm.Terminal();
        window.term = this.term!;
        const terminalElement = document.getElementById("terminal");
        if (!terminalElement) {
          reject(new Error("Terminal element not found"));
          return;
        }
        if (!this.term) {
          reject(new Error("Hterm not initialized"));
          return;
        }
        this.term.decorate(terminalElement);
        this.term.onTerminalReady = () => {
          const io = this.term!.io.push();
          io.onVTKeystroke = this.send;
          io.sendString = this.send;
          io.onTerminalResize = this.resize;
          this.term!.installKeyboard();
          this.term!.setCursorPosition(0, 0);
          this.term!.setCursorVisible(true);
          if (this.term!.screenSize) {
            this.resize(
              this.term!.screenSize!.width,
              this.term!.screenSize!.height,
            );
          }
          if (this.buffer) {
            this.term!.io.writeUTF16(this.buffer);
            this.buffer = "";
          }
          this.setTheme(this.currentTheme);
          resolve();
        };
      } catch (err) {
        reject(err);
      }
    });
  }

  setTheme(themeName: string): void {
    this.currentTheme = themeName;
    if (this.term) {
      this.term.setProfile(themeName);
    }
  }

  toggleTheme(): void {
    const newTheme =
      this.currentTheme === ThemeName.DEFAULT
        ? ThemeName.LIGHT
        : ThemeName.DEFAULT;
    this.setTheme(newTheme);
    this.focusTerminal();
  }

  addCustomTheme(name: string, theme: TerminalTheme): void {
    if (this.term) {
      this.term.setProfile(name);
      this.term.prefs_.importFromJson(theme);
    }
  }

  applyConfig(themeConfig: TerminalTheme): void {
    if (!this.term) {
      return;
    }
    this.term.prefs_.importFromJson(themeConfig);
    this.focusTerminal();
  }

  send = (text: string): void => {
    if (this.socket && this.isConnected) {
      this.socket.emit(SocketEvent.INPUT, text);
    }
  };

  private receive = (data: string): void => {
    if (!this.term) {
      this.buffer += data;
      return;
    }
    this.term.io.writeUTF16(data);
  };

  resize = (cols: number, rows: number): void => {
    if (this.socket && this.isConnected) {
      this.socket.emit(SocketEvent.RESIZE, {cols, rows});
    }
  };

  focusTerminal = (): void => {
    const terminalDiv = document.getElementById("terminal");
    if (terminalDiv) {
      terminalDiv.focus();
    }
  };

  disconnect = (): void => {
    if (this.socket) {
      this.socket.disconnect();
      this.socket = null;
    }
    this.isConnected = false;
  };

  clearTerminal(): void {
    if (this.term) {
      this.term.wipeContents();
    }
  }
}

export async function initializeTerminal(origin?: string): Promise<Terminal> {
  const terminal = Terminal.createInstance(origin);
  await terminal.initialize();
  return terminal;
}
