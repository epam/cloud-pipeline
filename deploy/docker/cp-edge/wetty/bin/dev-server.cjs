#!/usr/bin/env node
const express = require('express');
const { createServer } = require('http');
const { Server } = require('socket.io');
const { spawn } = require('node-pty');

function console_log(message) {
    console.log((new Date()) + ' ' + message);
}

function console_error(message) {
    console.error((new Date()) + ' ' + message);
}


function startDevServer(port = 3030) {
    const app = express();
    const server = createServer(app);
    const io = new Server(server, { path: '/ssh/socket.io' });

    io.on('connection', (socket) => {
        console_log('connected', socket.id);
        const shell = 'bash';

        // IPty + EventEmitter to access .on()
        const ptyProcess = spawn(shell, [], {
            name: 'xterm-256color',
            cols: 80,
            rows: 30,
            cwd: process.env.HOME,
            env: process.env,
        });

        ptyProcess.on('data', (data) => {
            socket.emit('output', data);
        });

        socket.on('input', (data) => {
            ptyProcess.write(data);
        });

        socket.on('resize', ({ cols, rows }) => {
            try {
                ptyProcess.resize(cols, rows);
            } catch (err) {
                if (err instanceof Error) {
                    console.error('Resize error:', err.message);
                }
            }
        });

        socket.on('disconnect', () => {
            console_log('disconnected', socket.id);
            ptyProcess.kill();
        });

        const theme = 'dracula';
        socket.on('term.ready', function() {
            console_log(`SSH GUI terminal -> ready, (re)setting theme "${theme}"`);
            socket.emit('term.theme', theme);
        });
        console_log(`SSH GUI terminal -> setting theme "${theme}"`);
        socket.emit('term.theme', theme);
    });

    server.listen(port, () => {
        console_log(`ssh server running at http://localhost:${port}`);
    });
}

const port = process.env.PORT ? Number(process.env.PORT) : 3030;
startDevServer(port);
