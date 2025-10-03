import {
  ThemeName,
  SocketEvent,
  type HtermTerminal,
  type SocketLike,
  type HtermPrefs,
  ConfigKeys,
} from "./types";
import { hterm } from "../../lib/hterm_all.js";
import socketIO from "socket.io-client";
import { DEFAULT_THEMES } from "./themes.js";
import {enableResources} from "./terminal-resources.ts";

export function getPreferenceInfo(terminal: Terminal, key: string) {
  if (!terminal?.prefs) {
    return { termDefault: undefined, themeDefault: undefined };
  }
  const termDefault = terminal!.prefs!.getDefault(key);
  const themeDefault = DEFAULT_THEMES[terminal?.currentTheme]?.[key];
  const value = terminal!.prefs!.get(key);
  return { value, termDefault, themeDefault };
}

const getThemeConfig = (terminal: Terminal, resetToDefaults = false) => {
  const keys = [
    ...new Set([
      ...Object.values(DEFAULT_THEMES).flatMap((theme) => Object.keys(theme)),
      ...Object.values(ConfigKeys),
    ]),
  ];
  const config = keys.reduce<{ [key: string]: unknown }>((config, key) => {
    const { value, termDefault, themeDefault } = getPreferenceInfo(
      terminal!,
      key
    );
    if (resetToDefaults) {
      config[key] = themeDefault ?? termDefault;
    } else {
      config[key] = value === termDefault ? themeDefault ?? termDefault : value;
    }
    return config;
  }, {});
  return config;
};

export class Terminal {
  private static instance: Terminal | null = null;
  private socket: SocketLike | null = null;
  public term: HtermTerminal | null = null;
  public prefs: HtermPrefs | null = null;
  private buffer: string = "";
  public currentTheme: string = localStorage.getItem('theme') || ThemeName.DEFAULT;
  private readonly origin: string;
  private isConnected: boolean = false;

  constructor() {
    this.origin = location.origin;
    if (WEB_SSH_ORIGIN !== "") {
      this.origin = WEB_SSH_ORIGIN;
    }
  }

  static getInstance(): Terminal | null {
    return Terminal.instance;
  }

  static createInstance(): Terminal {
    if (!Terminal.instance) {
      Terminal.instance = new Terminal();
    }
    return Terminal.instance;
  }

  async initialize(): Promise<void> {
    return new Promise((resolve, reject) => {
      try {
        this.socket = socketIO(this.origin, { path: "/ssh/socket.io" });
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
        this.prefs = this.term.prefs_;
        const terminalElement = document.getElementById("terminal");
        if (!terminalElement) {
          reject(new Error("Terminal element not found"));
          return;
        }
        if (!this.term) {
          reject(new Error("Hterm not initialized"));
          return;
        }
        this.term.prefs_.set("audible-bell-sound", "");
        this.term.decorate(terminalElement);
        enableResources(this.term.document_);

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
              this.term!.screenSize!.height
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

  setTheme(themeName: string): Promise<void> {
    return new Promise((resolve) => {
      if (this.term && this.prefs) {
        this.term.setProfile(themeName, async () => {
          themeName = themeName.toLowerCase();
          if (!DEFAULT_THEMES[themeName]) {
            console.error(`${themeName} theme not found`);
            themeName = ThemeName.DEFAULT;
          }
          this.currentTheme = themeName;
          const config = getThemeConfig(this);
          await this.prefs!.importFromJson(config);
          localStorage.setItem('theme', themeName);
          resolve();
        });
      } else {
        resolve();
      }
    });
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
      this.socket.emit(SocketEvent.RESIZE, { cols, rows });
    }
  };

  focusTerminal = (): void => {
    const terminalDiv = document.getElementById("terminal");
    if (terminalDiv) {
      terminalDiv.focus();
    }
  };

  resetTheme = async (theme: string) => {
    theme = theme.toLowerCase();
    const newTheme = DEFAULT_THEMES[theme];
    if (newTheme) {
      const config = getThemeConfig(this, true);
      await this.prefs!.importFromJson(config);
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

export async function initializeTerminal(): Promise<Terminal> {
  const terminal = Terminal.createInstance();
  await terminal.initialize();
  return terminal;
}
