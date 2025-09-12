export const Commands = {
  helloWorld: "remote-cp.helloWorld",
  openEmptyWindow: "remote-cp.openEmptyWindow",
  openEmptyWindowInCurrentWindow: "remote-cp.openEmptyWindowInCurrentWindow",
  showLog: "remote-cp.showLog",

  explorer: {
    emptyWindowInNewWindow: "remote-cp.explorer.emptyWindowInNewWindow",
    emptyWindowInCurrentWindow: "remote-cp.explorer.emptyWindowInCurrentWindow",
    refresh: "remote-cp.explorer.refresh",
    add: "remote-cp.explorer.add",
  },
} as const;
