var express = require('express');
var http = require('http');
var path = require('path');
var server = require('socket.io');
var pty = require('pty.js');
var request = require("sync-request");

function get_pipe_details(pipeline_id, auth_key) {
    api_url = process.env.API  + '/run/' + pipeline_id;
    var options = {
        'headers': {
            'Authorization': 'Bearer ' + auth_key
        }
    };
    var response = request('GET', api_url, options);
    if (response.statusCode == 200) {
        payload = JSON.parse(response.getBody()).payload;
        return {
            'ip': payload.podIP,
            'pass': payload.sshPassword,
            'owner': payload.owner,
            'pod_id': payload.podId

        };
    }

    return null;
}

var opts = require('optimist')
    .options({
        sshport: {
            demand: false,
            description: 'ssh server port'
        },
        sshuser: {
            demand: false,
            description: 'ssh user'
        },
	    sshpass: {
            demand: false,
            description: 'ssh pass'
        },
        port: {
            demand: true,
            alias: 'p',
            description: 'wetty listen port'
        },
    }).boolean('allow_discovery').argv;

var sshport = 22;
var sshuser = 'root';
var sshpass = null;

if (opts.sshport) {
    sshport = opts.sshport;
}

if (opts.sshuser) {
    sshuser = opts.sshuser;
}
if (opts.sshpass) {
    sshpass = opts.sshpass;
}

process.on('uncaughtException', function(e) {
    console.error('Error: ' + e);
});

var httpserv;

var app = express();
app.get('/ssh/pipeline/:pipeline', function(req, res) {
    res.sendfile(__dirname + '/public/ssh/index.html');
});
app.get('/ssh/container/:pipeline', function(req, res) {
    res.sendfile(__dirname + '/public/ssh/index.html');
});
app.use('/', express.static(path.join(__dirname, 'public')));


httpserv = http.createServer(app).listen(opts.port, function() {
    console.log('http on port ' + opts.port);
});

var io = server(httpserv,{path: '/ssh/socket.io'});
io.on('connection', function(socket){
    var sshhost = 'localhost';
    var pipeline_id = 0
    var request = socket.request;
    console.log((new Date()) + ' Connection accepted for ' + request.headers.referer);
    var term;
    if (match = request.headers.referer.match('/ssh/(pipeline|container)/(.+)$')) {
        pipeline_id = match[2];
        if (!pipeline_id){
            socket.disconnect();
            return;
        }

        var auth_key = socket.handshake.headers['token'];
        pipe_details = get_pipe_details(pipeline_id, auth_key)
        if (!pipe_details || !pipe_details.ip || !pipe_details.pass)
        {
            console.log((new Date()) + " Cannot get ip/pass for a run #" + pipeline_id);
            socket.disconnect();
            return;
        }

        if(match[1] == "pipeline"){
            sshhost = pipe_details.ip;
            sshpass = pipe_details.pass;
            sshuser = 'root';
            term = pty.spawn('sshpass', ['-p', sshpass, 'ssh', sshuser + '@' + sshhost, '-p', sshport, '-o', 'StrictHostKeyChecking=no', '-o', 'GlobalKnownHostsFile=/dev/null', '-o', 'UserKnownHostsFile=/dev/null', '-q'], {
                    name: 'xterm-256color',
                    cols: 80,
                    rows: 30
            });
        } else if (match[1] == "container") {
            console.log((new Date()) + ' Trying to exec kubectl exec for pod: ' + pipe_details.pod_id);
            term = pty.spawn('kubectl', ['exec', '-it', pipe_details.pod_id, '/bin/bash'], {
                    name: 'xterm-256color',
                    cols: 80,
                    rows: 30
            });
        } else {
            socket.disconnect();
            return;
        }
    }
    else {
        socket.disconnect();
        return;
    }


    console.log((new Date()) + " PID=" + term.pid + " STARTED to IP=" + sshhost + ", RUNNO=" + pipeline_id + " on behalf of user=" + sshuser);
    term.on('data', function(data) {
        socket.emit('output', data);
    });
    term.on('exit', function(code) {
        console.log((new Date()) + " PID=" + term.pid + " ENDED")
    });
    socket.on('resize', function(data) {
        term.resize(data.col, data.row);
    });
    socket.on('input', function(data) {
        term.write(data);
    });
    socket.on('disconnect', function() {
        console.log((new Date()) + " Disconnecting PID=" + term.pid);
        term.end();
        try {
            process.kill(term.pid);
        }
        catch(ex) {
            console.log(ex);
        }
    });
})
