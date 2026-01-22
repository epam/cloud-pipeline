"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.findRandomPort = findRandomPort;
exports.findFreePort = findFreePort;
exports.findFreePortFaster = findFreePortFaster;
const net = __importStar(require("net"));
/**
 * Finds a random unused port assigned by the operating system. Will reject in case no free port can be found.
 */
function findRandomPort() {
    return new Promise((resolve, reject) => {
        const server = net.createServer({ pauseOnConnect: true });
        server.on("error", reject);
        server.on("listening", () => {
            const port = server.address().port;
            server.close(() => resolve(port));
        });
        server.listen(0, "127.0.0.1");
    });
}
/**
 * Given a start point and a max number of retries, will find a port that
 * is openable. Will return 0 in case no free port can be found.
 */
function findFreePort(startPort, giveUpAfter, timeout, stride = 1) {
    let done = false;
    return new Promise((resolve) => {
        const timeoutHandle = setTimeout(() => {
            if (!done) {
                done = true;
                return resolve(0);
            }
        }, timeout);
        doFindFreePort(startPort, giveUpAfter, stride, (port) => {
            if (!done) {
                done = true;
                clearTimeout(timeoutHandle);
                return resolve(port);
            }
        });
    });
}
function doFindFreePort(startPort, giveUpAfter, stride, clb) {
    if (giveUpAfter === 0) {
        return clb(0);
    }
    const client = new net.Socket();
    // If we can connect to the port it means the port is already taken so we continue searching
    client.once("connect", () => {
        dispose(client);
        return doFindFreePort(startPort + stride, giveUpAfter - 1, stride, clb);
    });
    client.once("data", () => {
        // this listener is required since node.js 8.x
    });
    client.once("error", (err) => {
        dispose(client);
        // If we receive any non ECONNREFUSED error, it means the port is used but we cannot connect
        if (err.code !== "ECONNREFUSED") {
            return doFindFreePort(startPort + stride, giveUpAfter - 1, stride, clb);
        }
        // Otherwise it means the port is free to use!
        return clb(startPort);
    });
    client.connect(startPort, "127.0.0.1");
}
/**
 * Uses listen instead of connect. Is faster, but if there is another listener on 0.0.0.0 then this will take 127.0.0.1 from that listener.
 */
function findFreePortFaster(startPort, giveUpAfter, timeout) {
    let resolved = false;
    let timeoutHandle = undefined;
    let countTried = 1;
    const server = net.createServer({ pauseOnConnect: true });
    function doResolve(port, resolve) {
        if (!resolved) {
            resolved = true;
            server.removeAllListeners();
            server.close();
            if (timeoutHandle) {
                clearTimeout(timeoutHandle);
            }
            resolve(port);
        }
    }
    return new Promise((resolve) => {
        timeoutHandle = setTimeout(() => {
            doResolve(0, resolve);
        }, timeout);
        server.on("listening", () => {
            doResolve(startPort, resolve);
        });
        server.on("error", (err) => {
            if (err &&
                (err.code === "EADDRINUSE" || err.code === "EACCES") &&
                countTried < giveUpAfter) {
                startPort++;
                countTried++;
                server.listen(startPort, "127.0.0.1");
            }
            else {
                doResolve(0, resolve);
            }
        });
        server.on("close", () => {
            doResolve(0, resolve);
        });
        server.listen(startPort, "127.0.0.1");
    });
}
function dispose(socket) {
    try {
        socket.removeAllListeners("connect");
        socket.removeAllListeners("error");
        socket.end();
        socket.destroy();
        socket.unref();
    }
    catch (error) {
        console.error(error); // otherwise this error would get lost in the callback chain
    }
}
//# sourceMappingURL=ports.js.map