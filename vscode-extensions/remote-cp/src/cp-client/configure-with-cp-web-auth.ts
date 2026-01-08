import * as vscode from "vscode";
import * as http from "http";
import open from "open";
import { StringDecoder } from "string_decoder";

import { ICpExtConfig } from "../config";
import { ILogger } from "../common/logger";
import { ICpClientConfig } from "./cp-client-config";
import { CpAuthInvalidError, UserCancelledError } from "./error";
import { findRandomPort } from "../common/ports";
import { quickPickWithCountdown } from "../common/quick-pick-with-countdown";

export async function configureWithCpUrl(
  cpExtConfig: ICpExtConfig,
  logger: ILogger,
): Promise<ICpClientConfig | null> {
  const logPfx = "web auth:";
  const callbackPort = await findRandomPort();

  return new Promise<ICpClientConfig>((resolve, reject) => {
    let settled = false;
    let disposed = false;
    let server: http.Server | null = http.createServer(async (req, res) => {
      try {
        let resConfig: ICpClientConfig | null = null;
        if (
          req.url?.startsWith("/callback") &&
          req.method === "POST" &&
          req.headers["content-type"] === "application/x-www-form-urlencoded"
        ) {
          logger.debug(`${logPfx} get callback request expected`);
          const body = await readReqBody(req);
          const form = new URLSearchParams(body);
          const apiUri: string = vscode.Uri.parse(cpExtConfig.platformUrl)
            .with({ path: cpExtConfig.apiEndpoint })
            .toString();
          const apiToken: string | null = form.get("bearer");
          if (apiUri && apiToken) {
            resConfig = { apiUri, apiToken };
            logger.debug(`${logPfx} config obtained with token.`);
          }
        }

        if (resConfig) {
          responseSuccess(res, cpExtConfig);
          settleResolve(resConfig);
        } else {
          responseError(res, cpExtConfig);
          settleReject(new CpAuthInvalidError());
        }
      } finally {
        dispose();
      }
    });

    let timeoutHandler: NodeJS.Timeout | null = null;

    const settleResolve = (config: ICpClientConfig): void => {
      if (settled) {
        return;
      }
      settled = true;
      dispose();
      resolve(config);
    };

    const settleReject = (err: unknown): void => {
      if (settled) {
        return;
      }
      settled = true;
      dispose();
      reject(err);
    };

    function dispose(): void {
      if (disposed) {
        return;
      }
      disposed = true;
      logger.info(`${logPfx} close.`);
      if (server) {
        server.close();
        server = null;
      }
      if (timeoutHandler) {
        clearTimeout(timeoutHandler);
        timeoutHandler = null;
      }
      if (quickPick) {
        quickPick.dispose();
      }
    }

    timeoutHandler = setTimeout(() => {
      settleReject(new Error(`Timeout for ${logPfx} callback`));
    }, 600000);

    server.listen(callbackPort, "127.0.0.1");
    logger.info(`${logPfx} server listens at port ${callbackPort}`);

    const quickPick = quickPickWithCountdown(
      "Waiting for the Web Auth callback from the web browser...",
      [{ label: "Cancel" }],
      180000,
    );
    quickPick.result
      .then((item) => {
        if (!settled && item.label === "Cancel") {
          settleReject(new UserCancelledError("Auth cancelled by user"));
        }
      })
      .catch((err) => {
        if (!settled) {
          settleReject(err);
        }
      });

    const callbackUri = vscode.Uri.parse(
      `http://localhost:${callbackPort}`,
    ).with({
      path: "/callback",
    });
    const authUrl = vscode.Uri.parse(cpExtConfig.platformUrl).with({
      path: cpExtConfig.authEndpoint,
      query: new URLSearchParams({
        url: callbackUri.toString(),
        type: "FORM",
      }).toString(),
    });

    const authUrlStr = authUrl.toString(true);
    void open(authUrlStr);
    logger.info(`${logPfx} browser open at '${authUrlStr}'.`);
  });
}

async function readReqBody(req: http.IncomingMessage): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    try {
      let body = "";
      const decoder = new StringDecoder("utf-8");

      req.on("data", (chunk) => {
        body += decoder.write(chunk);
      });

      req.on("end", () => {
        body += decoder.end();

        resolve(body);
      });
    } catch (err) {
      reject(err);
    }
  });
}

function responseSuccess(
  res: http.ServerResponse,
  cpExtConfig: ICpExtConfig,
): void {
  res.writeHead(200, {
    "Content-Type": "text/html",
  });
  // prettier-ignore
  res.end(
    "<h1>Success</h1>" + "\n" +
    `<p>VSCode extension 'remote-cp' ${cpExtConfig.prefix} obtained the access token. </p>` + "\n" +
    "<p>You can close this tab now</p>",
  );
}

function responseError(
  res: http.ServerResponse,
  cpExtConfig: ICpExtConfig,
): void {
  res.writeHead(400, {
    "Content-Type": "text/html",
  });
  // prettier-ignore
  res.end(
    "<h1>Error</h1>" + "\n"+
    `<p>VSCode extension 'remote-cp' ${cpExtConfig.prefix} failed to obtain access token.</p>` + "\n" +
    "<p>You can close this tab now</p>",
  );
}
