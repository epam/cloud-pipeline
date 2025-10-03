const express = require('express');
const http = require('http');
const path = require('path');
const Server = require('socket.io');
const pty = require('node-pty');

const app = express();
const server = http.createServer(app);
const io = new Server(server);

// Local pty server for Wetty mocks ('node path/to/server.js')

const PORT = 3030;

app.use(express.static(path.join(__dirname, '../public/ssh')));

io.on('connection', (socket) => {
  const shell = 'bash';
  const ptyProcess = pty.spawn(shell, [], {
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
    if (ptyProcess && ptyProcess._writable) {
      try {
        console.log(ptyProcess)
        ptyProcess.resize(cols, rows);
      } catch (err) {
        console.error('Resize error:', err.message);
      }
    } else {
      console.warn('Resize error.');
    }
  });
  socket.on('disconnect', () => {
    ptyProcess.kill();
  });
});

server.listen(PORT, () => {
  console.log(`Wetty server running at http://localhost:${PORT}`);
});
