#!/usr/bin/env node
import express, { type Express } from 'express';
import { Server as HTTPServer, createServer } from 'http';
import { Server as SocketIOServer, type Socket } from 'socket.io';
import { type IPty, spawn } from 'node-pty';
import { EventEmitter } from 'events';

function startDevServer(port: number = 3030): void {
    const app: Express = express();
    const server: HTTPServer = createServer(app);
    const io = new SocketIOServer(server, { path: '/ssh/socket.io' });

    io.on('connection', (socket: Socket) => {
        console.log('connected', socket.id);
        const shell = 'bash';

        // IPty + EventEmitter to access .on()
        const ptyProcess: IPty & EventEmitter = spawn(shell, [], {
            name: 'xterm-256color',
            cols: 80,
            rows: 30,
            cwd: process.env.HOME,
            env: process.env as NodeJS.ProcessEnv,
        }) as IPty & EventEmitter;

        ptyProcess.on('data', (data: string) => {
            socket.emit('output', data);
        });

        socket.on('input', (data: string) => {
            ptyProcess.write(data);
        });

        socket.on('resize', ({ cols, rows }: { cols: number; rows: number }) => {
            try {
                ptyProcess.resize(cols, rows);
            } catch (err) {
                if (err instanceof Error) {
                    console.error('Resize error:', err.message);
                }
            }
        });

        socket.on('disconnect', () => {
            console.log('disconnected', socket.id);
            ptyProcess.kill();
        });
    });

    server.listen(port, () => {
        console.log(`ssh server running at http://localhost:${port}`);
    });
}

const port = process.env.PORT ? Number(process.env.PORT) : 3030;
startDevServer(port);
