import { Terminal, Terminal as XTerm, type ITerminalOptions } from "@xterm/xterm";

import {
  ThemeName,
  SocketEvent,
  type SocketLike,
  ConfigKeys,
  type TerminalTheme,
  type ParameterValue,
  type ANSIPalette,
  type TerminalThemes,
} from "../types.ts";
import socketIO from "socket.io-client";
import { FitAddon } from "@xterm/addon-fit";
import { getThemes, getXTermOptions, setXTermParameter, setXTermParameters } from "./terminal-helpers.ts";

const LS_KEYS_TO_CLEAR = ["user-preferred-theme", "themes-touched"];

declare global {
  interface Window {
    term?: Terminal;
  }
}

function markThemeTouched(theme: string) {
  try {
    const info = JSON.parse(localStorage.getItem("themes-touched") ?? "{}");
    info[theme] = true;
    localStorage.setItem("themes-touched", JSON.stringify(info));
  } catch (e) {
    console.error("Error while setting preference, ", e);
  }
}

function unmarkThemeTouched(theme: string) {
  try {
    const info = JSON.parse(localStorage.getItem("themes-touched") ?? "{}");
    delete info[theme];
    if (Object.keys(info).length > 0) {
      localStorage.setItem("themes-touched", JSON.stringify(info));
    } else {
      localStorage.removeItem("themes-touched");
    }
  } catch (e) {
    console.error("Error while setting preference, ", e);
  }
}

export class XTerminal {
  // Terminal specific fields
  private static instance: XTerminal | null = null;
  private terminalElement = document.getElementById("terminal");
  private socket: SocketLike | null = null;
  public term: Terminal | null = null;
  private buffer: string = "";
  private readonly origin: string;
  private isConnected: boolean = false;
  public initialized: boolean = false;

  // Theme specific fields
  public themes: TerminalThemes; // themes with changed parameters
  public defaultThemes: TerminalThemes; // themes with default parameters
  public currentThemeName: string = this.userPreferredTheme || ThemeName.DEFAULT;
  public platformPreferredTheme: string = "";
  
  // Other
  private resizeObserver: ResizeObserver | null = null;
  

  constructor() {
    this.origin = location.origin;
    if (WEB_SSH_ORIGIN !== "") {
      this.origin = WEB_SSH_ORIGIN;
    }
    const {themes, defaultThemes} = getThemes();
    this.themes = themes;
    this.defaultThemes = defaultThemes;
  }

  static getInstance(): XTerminal | null {
    return XTerminal.instance;
  }

  static createInstance(): XTerminal {
    if (!XTerminal.instance) {
      XTerminal.instance = new XTerminal();
    }
    return XTerminal.instance;
  }

  get theme () {
    return this.themes[this.currentThemeName] || this.themes[ThemeName.DEFAULT]
  };

  get defaultTheme () {
    return this.defaultThemes[this.currentThemeName] || this.defaultThemes[ThemeName.DEFAULT]
  };

  get userPreferredTheme() {
    return localStorage.getItem("user-preferred-theme");
  }

  get preferencesTouched() {
    return Object.keys(localStorage).some((key: string) =>
      LS_KEYS_TO_CLEAR.includes(key)
    );
  }

  async initialize(): Promise<void> {
    console.log("Socket.io -> initialization");
    const socket = (this.socket = socketIO(this.origin, {
      path: "/ssh/socket.io",
    }));
    socket.on(SocketEvent.CONNECT, () => {
      console.log('Socket.io -> "connect" event');
      if (this.term) {
        this.isConnected = true;
        this.resize({cols: this.term.cols, rows: this.term.rows});
      }
    });
    socket.on(SocketEvent.OUTPUT, (data?: unknown) => {
      if (typeof data === "string") {
        this.receive(data);
      }
    });
    socket.on(SocketEvent.DISCONNECT, () => {
      console.log('Socket.io -> "disconnect" event');
      this.isConnected = false;
      console.log("Socket.io connection closed");
    });
    let themeRequest: string | undefined;
    const setTheme = async (theme?: string) => {
      const t = theme ?? themeRequest;
      if (t) {
        if (this.initialized) {
          console.log(`term -> set theme to "${t}"`);
          await this.setTheme(t);
        } else {
          console.log(
            `term -> set theme request to "${t}" (waiting for initialization)`
          );
          themeRequest = t;
        }
      }
    };
    socket.on(SocketEvent.THEME, (sshTheme: unknown) => {
      console.log('Socket.io -> "term.theme" event, payload:', sshTheme);
      if (typeof sshTheme !== "string") {
        return;
      }
      if (this.userPreferredTheme) {
        console.log(
          `term -> skipping changing theme to "${sshTheme}" (user selected "${this.userPreferredTheme}" theme)`
        );
      } else {
        setTheme(sshTheme);
      }
      this.platformPreferredTheme = sshTheme;
    });
    await this.initializeXterm();
    await setTheme();
    console.log("Socket.io -> emitting ready event");
    socket.emit(SocketEvent.READY, "ready");
  }

  private async initializeXterm(): Promise<void> {
    const preferences = getXTermOptions(this.theme) as ITerminalOptions;
    const term = (this.term = new XTerm(preferences));
    const fitAddon = new FitAddon();
    window.term = term;
    if (!this.terminalElement) {
      console.error("term -> terminal element not found");
      throw new Error("Terminal element not found");
    }
    await new Promise<void>((resolve) => {
      console.log("term -> decorating terminal");
      term.loadAddon(fitAddon);
      term.open(this.terminalElement!);
      fitAddon.fit();
      term.onData(this.send);
      term.onResize(this.resize);
      this.resizeObserver = new ResizeObserver(() => {
        fitAddon.fit();
      });
      this.resizeObserver.observe(this.terminalElement!);
      if (this.buffer) {
        this.receive(this.buffer);
        this.buffer = "";
      }
      this.resize({cols: term.cols, rows: term.rows});
      resolve();
    });
    console.log("term -> ready");
    await this.setTheme(this.currentThemeName);
    this.initialized = true;
    this.focus();
    console.log("term -> initialized");
  }

  setParameters = (
    parameters: TerminalTheme,
    cb?: (parameters: TerminalTheme) => void
  ) => {
    setXTermParameters(parameters, this);
    if (cb) {
      cb(this.theme);
    }
  };

  setParameter = (
    key: ConfigKeys,
    value: ParameterValue | ANSIPalette
  ) => {
    if (typeof value === "string" || typeof value === "number" || typeof value === "boolean" || typeof value === 'object') {
      console.log(value)
      setXTermParameter(key, value as ParameterValue, this);
    }
    markThemeTouched(this.currentThemeName);
  };

  setTheme(
    themeName: string,
    isUserPreferred = false,
    cb?: (parameters: TerminalTheme) => void,
  ): Promise<void> {
    return new Promise((resolve) => {
      if (this.term) {
        themeName = themeName.toLowerCase();
        if (!this.themes[themeName]) {
          console.error(`${themeName} theme not found`);
          themeName = ThemeName.DEFAULT;
        }
        if (isUserPreferred && this.themes[themeName]) {
          localStorage.setItem("user-preferred-theme", themeName);
        }
        this.currentThemeName = themeName;
        this.setParameters(this.theme);
        if (cb) {
          cb(this.theme);
        }
        resolve();
      } else {
        resolve();
      }
    });
  }

  getParameters = (): TerminalTheme => {
    return {
      ...this.defaultTheme,
      ...this.theme
    }
  };

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
    this.term.write(data);
  };

  resize = ({cols, rows}: {cols: number, rows: number}): void => {
    if (this.socket && this.isConnected) {
      console.log(`term -> resize (${cols} x ${rows})`);
      console.log(`Socket.io -> emitting resize event (${cols} x ${rows})`);
      this.socket.emit(SocketEvent.RESIZE, { cols, rows });
    } else {
      console.log(`term -> skipping resize (${cols} x ${rows}), not connected`);
    }
  };

  focus = (): void => {
    this.term?.focus();
  };

  resetToDefaults = () => {
    if (!this.term) {
      return;
    }
    LS_KEYS_TO_CLEAR.forEach((key) => localStorage.removeItem(key));
    this.themes = getThemes().defaultThemes;
    this.setTheme(this.platformPreferredTheme ?? ThemeName.DEFAULT);
  };

  resetTheme = (theme: string) => {
    theme = theme.toLowerCase();
    const newTheme = this.themes[theme];
    if (newTheme) {
      const {defaultThemes} = getThemes(true);
      this.setParameters(defaultThemes[theme]);
      unmarkThemeTouched(theme);
    }
  };

  // disconnect = (): void => {
  //   if (this.socket) {
  //     this.socket.disconnect();
  //     this.socket = null;
  //   }
  //   this.isConnected = false;
  // };

  dispose = (): void => {
    try {
      if (this.resizeObserver && this.terminalElement) {
        this.resizeObserver.unobserve(this.terminalElement);
      }
      this.resizeObserver?.disconnect();
      this.term?.dispose();
      if (this.socket) {
        this.socket.disconnect();
      }
    } catch {
      // noop
    } finally {
      this.resizeObserver = null;
      this.term = null;
      this.socket = null;
      this.isConnected = false;
    }
    XTerminal.instance = null;
  };
}

export async function initializeXTerm(): Promise<XTerminal> {
  const terminal = XTerminal.createInstance();
  await terminal.initialize();
  return terminal;
}
