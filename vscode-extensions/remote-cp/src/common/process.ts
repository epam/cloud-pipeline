export async function waitForProcessExit(
  pid: number,
  options?: {
    kill: boolean /* false */;
    timeoutMs?: number /* 10000 */;
    checkIntervalMs?: number /* 250 */;
    timeoutMsg?: string;
  },
): Promise<void> {
  const kill = options?.kill ?? false;
  const timeoutMs = options?.timeoutMs ?? 10000;
  const checkIntervalMs = options?.checkIntervalMs ?? 250;
  const timeoutMsg =
    options?.timeoutMsg ?? `Timeout waiting for process ${pid} to exit.`;

  const started = Date.now();
  while (true) {
    if (kill) {
      try {
        process.kill(pid, "SIGTERM");
      } catch (err: any) {
        if (err && err.code === "ESRCH") return;
        throw err;
      }
    }

    if (Date.now() - started > timeoutMs) {
      throw new Error(timeoutMsg);
    }
    await new Promise((res) => setTimeout(res, checkIntervalMs));
  }
}
