var express = require('express');
var http = require('http');
var path = require('path');
var server = require('socket.io');
var pty = require('pty.js');
var request = require("sync-request");
var child_process = require('child_process');

// Constants
var ENV_TAG_RUNID_NAME = 'CP_ENV_TAG_RUNID';
var CONN_QUOTA_PER_PIPELINE_ID = process.env.CP_EDGE_MAX_SSH_CONNECTIONS || 25;


function call_api(api_method, auth_key) {
    var options = {
        'headers': {
            'Authorization': 'Bearer ' + auth_key
        }
    };
    var response = request('GET', process.env.API + api_method, options);
    if (response.statusCode == 200) {
        return JSON.parse(response.getBody()).payload;
    }
    return null;
}

function get_pipe_details(pipeline_id, auth_key) {
    payload = call_api('/run/' + pipeline_id, auth_key);
    if (payload) {
        return {
            'ip': payload.podIP,
            'pass': payload.sshPassword,
            'owner': payload.owner,
            'pod_id': payload.podId
        };
    } else {
        return payload;
    }
}

function get_user_details(auth_key) {
    payload = call_api('/whoami', auth_key);
    if (payload) {
        return {
            'id': payload.id,
            'name': payload.userName
        };
    } else {
        return payload;
    }
}

function get_preference(preference, auth_key) {
    payload = call_api('/preferences/' + preference, auth_key);
    return payload && payload.value;
}

function get_boolean_preference(preference, auth_key) {
    value = get_preference(preference, auth_key);
    if (value) {
        return value.toLowerCase().trim() === 'true';
    } else {
        return false;
    }
}

function conn_quota_available(pipeline_id) {
    // This command:
    // 1. Searches for the processes with "ssh" command (grep -wl ssh /proc/*/comm) and prints the file name (e.g. /proc/123/comm)
    // 2. Replaces the "comm" with "environ" file name (it will be searched for a "tag")
    // 3. Searches the resulting list of "ssh" processes, that are "tagged" with the RUN ID
    var running_pids_command = 'ssh_pids=$(grep -wl ssh /proc/*/comm | sed -e "s/comm/environ/g") && \
                                [ "$ssh_pids" ] && \
                                grep -l "\\b' + ENV_TAG_RUNID_NAME + '=' + pipeline_id + '\\b" $ssh_pids';

    var running_pids_count = 0;
    try {
        var stdout = child_process.execSync(running_pids_command).toString();
        running_pids_count = stdout.split(/\r\n|\r|\n/).filter(item => item).length;
    } 
    catch (err) {
        // (1) means that there is no match (i.e. no ssh processes with the correct "tag")
        // This is totally ok behavior and we consider that quota IS available
        // https://www.unix.com/man-page/posix/1P/grep/
        if (err.status != 1) {
            // If something went wrong - we'll be optimists and allow the connection, but drop a log line on the error
            console.log((new Date()) + ' Cannot get running connections for a run #' + pipeline_id + ' (exit code: ' + err.status + ', stderr: ' + err.stderr + ')');
        }
    }

    console.log('Already running "' + running_pids_count + '" PIDs for #' + pipeline_id);
    return running_pids_count < CONN_QUOTA_PER_PIPELINE_ID;;
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
io.on('connection', function(socket) {
    var sshhost = 'localhost';
    var pipeline_id = 0
    var request = socket.request;
    console.log((new Date()) + ' Connection accepted for ' + request.headers.referer);
    var term;
    if (match = request.headers.referer.match('/ssh/(pipeline|container)/(.+)$')) {
        pipeline_id = match[2];
        if (!pipeline_id) {
            socket.disconnect();
            return;
        }

        if (!conn_quota_available(pipeline_id)) {
            var conn_err_msg = ' SSH connection quota exceeded for the run #' + pipeline_id + ' (Max connections: ' + CONN_QUOTA_PER_PIPELINE_ID + ')';
            console.log((new Date()) + conn_err_msg);
            socket.emit('output', conn_err_msg);
            socket.disconnect();
            return;
        }

        var auth_key = socket.handshake.headers['token'];
        pipe_details = get_pipe_details(pipeline_id, auth_key);
        if (!pipe_details || !pipe_details.ip || !pipe_details.pass) {
            console.log((new Date()) + " Cannot get ip/pass for a run #" + pipeline_id);
            socket.disconnect();
            return;
        }

        user_details = get_user_details(auth_key);
        if (!user_details || !user_details.name) {
            console.log((new Date()) + " Cannot get an authenticated user name");
            socket.disconnect();
            return;
        }

        ssh_default_root_user_enabled = get_boolean_preference('system.ssh.default.root.user.enabled', auth_key);

        if(match[1] == "pipeline") {
            sshhost = pipe_details.ip;
            if (ssh_default_root_user_enabled) {
                sshpass = pipe_details.pass;
                sshuser = 'root';
            } else {
                sshpass = user_details.name;
                sshuser = user_details.name;
            }
            term = pty.spawn('sshpass', ['-p', sshpass, 'ssh', sshuser + '@' + sshhost, '-p', sshport, '-o', 'StrictHostKeyChecking=no', '-o', 'GlobalKnownHostsFile=/dev/null', '-o', 'UserKnownHostsFile=/dev/null', '-q'], {
                    name: 'xterm-256color',
                    cols: 80,
                    rows: 30,
                    env: { [ENV_TAG_RUNID_NAME]: pipeline_id }
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
