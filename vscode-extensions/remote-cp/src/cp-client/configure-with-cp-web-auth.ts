import * as vscode from "vscode";
import * as http from "http";
import open from "open";
import { StringDecoder } from "string_decoder";

import { ICpExtConfig } from "../config";
import { ILogger } from "../common/logger";
import { ICpClientConfig } from "./cp-client-config";
import { CpAuthInvalidError } from "./error";
import { findRandomPort } from "../common/ports";

export async function configureWithCpUrl(
  cpExtConfig: ICpExtConfig,
  logger: ILogger,
): Promise<ICpClientConfig | null> {
  const logPfx = "web auth:";
  const callbackPort = await findRandomPort();

  return new Promise<ICpClientConfig>((resolve, reject) => {
    let server: http.Server | null = http.createServer(async (req, res) => {
      try {
        let resConfig: ICpClientConfig | null = null;
        if (
          req.url?.startsWith("/callback") &&
          req.method === "POST" &&
          req.headers["content-type"] === "application/x-www-form-urlencoded"
        ) {
          const body = await readReqBody(req);
          const form = new URLSearchParams(body);
          const apiUri: string = vscode.Uri.parse(cpExtConfig.platformUrl)
            .with({ path: cpExtConfig.apiEndpoint })
            .toString();
          const apiToken: string | null = form.get("bearer");
          if (apiUri && apiToken) {
            resConfig = { apiUri, apiToken };
            logger.info(`${logPfx} config obtained with token.`);
          }
        }

        if (resConfig) {
          responseSuccess(res, cpExtConfig);
          resolve(resConfig);
        } else {
          responseError(res, cpExtConfig);
          reject(new CpAuthInvalidError());
        }
      } finally {
        close();
      }
    });

    let timeoutHandler: NodeJS.Timeout | null = null;

    function close(): void {
      logger.info(`${logPfx} close.`);
      if (server) {
        server.close();
        server = null;
      }
      if (timeoutHandler) {
        clearTimeout(timeoutHandler);
        timeoutHandler = null;
      }
    }

    timeoutHandler = setTimeout(() => {
      close();
      reject(new Error(`Timeout for ${logPfx} callback`));
    }, 600000);

    server.listen(callbackPort, "127.0.0.1");
    logger.info(`${logPfx} server listens at port ${callbackPort}`);

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

    void open(authUrl.toString(true));
    logger.info(`${logPfx} browser open at '${authUrl}'.`);
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
