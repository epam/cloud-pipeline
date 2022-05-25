var express = require('express');
var http = require('http');
var path = require('path');
var server = require('socket.io');
var pty = require('pty.js');
var request = require("sync-request");
var child_process = require('child_process');

const checkMaintenanceMode = require('./maintenance');

// Constants
var ENV_TAG_RUNID_NAME = 'CP_ENV_TAG_RUNID';
var CONN_QUOTA_PER_PIPELINE_ID = process.env.CP_EDGE_MAX_SSH_CONNECTIONS || 25;

const CP_MAINTENANCE_SKIP_ROLES = (process.env.CP_MAINTENANCE_SKIP_ROLES || '')
    .split(',')
    .map(o => o.trim().toUpperCase())
    .filter(o => o.length);

function call_api(api_method, auth_key, httpMethod = 'GET', payload = undefined) {
    var options = {
        'headers': {
            'Authorization': 'Bearer ' + auth_key
        },
        json: payload
    };
    var response = request(httpMethod, process.env.API + api_method, options);
    if (response.statusCode == 200) {
        return JSON.parse(response.getBody()).payload;
    }
    return null;
}

function get_pipe_details(pipeline_id, auth_key) {
    payload = call_api('/run/' + pipeline_id, auth_key);
    if (payload) {
        var parameters = {};
        if (payload.pipelineRunParameters) {
            parameters = payload.pipelineRunParameters.reduce(function(parameters, parameter) {
                parameters[parameter.name] = parameter.value;
                return parameters;
            }, {});
        }
        return {
            'ip': payload.podIP,
            'pass': payload.sshPassword,
            'owner': payload.owner,
            'pod_id': payload.podId,
            'platform': payload.platform,
            'parameters': parameters
        };
    } else {
        return payload;
    }
}

function get_current_user(auth_key) {
    payload = call_api('/whoami', auth_key);
    if (payload) {
        return {
            'id': payload.id,
            'name': payload.userName,
            'admin': payload.admin,
            'roles': (payload.groups || []).concat((payload.roles || []).map(function (role) { return role.name; }))
        };
    } else {
        return payload;
    }
}

function get_platform_name(auth_key) {
    const payload = call_api('/preferences/ui.pipeline.deployment.name', auth_key);
    if (payload && payload.value) {
        return payload.value;
    }
    return 'Cloud Pipeline';
}

function get_ssh_theme(auth_key) {
    const payload = call_api('/preferences/ui.ssh.theme', auth_key);
    if (payload && payload.value) {
        return payload.value;
    }
    return 'default';
}

function get_user_attributes_theme(userId, auth_key) {
    const payload = call_api(
        '/metadata/load',
         auth_key,
        'POST',
        [{
            entityClass: 'PIPELINE_USER',
            entityId: userId
        }]
    );
    if (payload && payload.length) {
        const {data = {}} = payload[0];
        const {['ui.ssh.theme']: userDefinedTheme} = data;
        if (userDefinedTheme && userDefinedTheme.value) {
            return userDefinedTheme.value;
        }
    }
    return 'default';
}

function get_boolean(value) {
    return value ? value.toLowerCase().trim() === 'true' : false;
}

function get_preference(preference, auth_key) {
    payload = call_api('/preferences/' + preference, auth_key);
    return payload && payload.value;
}

function get_boolean_preference(preference, auth_key) {
    return get_boolean(get_preference(preference, auth_key));
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

function get_owner_user_name(owner) {
    // split owner by @ in case it represented by email address
    return owner.split("@")[0];
}

function get_run_sshpass(run_details, auth_key) {
    parent_run_id = run_details.parameters['parent-id'];
    run_shared_users_enabled = get_boolean(run_details.parameters['CP_CAP_SHARE_USERS']);
    if (run_shared_users_enabled && parent_run_id) {
        parent_run_details = get_pipe_details(parent_run_id, auth_key);
        return parent_run_details.pass;
    } else {
        return run_details.pass;
    }
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

const {addListener} = checkMaintenanceMode();

var io = server(httpserv,{path: '/ssh/socket.io'});
io.on('connection', function(socket) {
    var sshhost = 'localhost';
    var pipeline_id = 0
    var request = socket.request;
    console.log((new Date()) + ' Connection accepted for ' + request.headers.referer);
    var term;
    let current_user;
    let platformName = 'Cloud Pipeline';
    let theme = 'default';
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
        if (!pipe_details || !pipe_details.ip || !pipe_details.pass || !pipe_details.owner) {
            console.log((new Date()) + " Cannot get ip/pass/owner for a run #" + pipeline_id);
            socket.disconnect();
            return;
        }

        if(match[1] == 'pipeline') {
            sshhost = pipe_details.ip;
            run_ssh_mode = pipe_details.parameters['CP_CAP_SSH_MODE'];
            owner_user_name = get_owner_user_name(pipe_details.owner);
            if (!run_ssh_mode) {
                if (pipe_details.platform == 'windows') {
                    run_ssh_mode = 'owner-sshpass';
                } else {
                    run_ssh_mode = get_boolean_preference('system.ssh.default.root.user.enabled', auth_key) ? 'root' : 'owner';
                }
            }
            current_user = get_current_user(auth_key);
            platformName = get_platform_name(auth_key);
            const userDefinedTheme = current_user
                ? get_user_attributes_theme(current_user.id, auth_key)
                : undefined;
            theme = userDefinedTheme || get_ssh_theme(auth_key);
            switch (run_ssh_mode) {
                case 'user':
                    user = current_user;
                    sshuser = user.name;
                    sshpass = user.name;
                    break
                case 'owner':
                    sshuser = owner_user_name;
                    sshpass = owner_user_name;
                    break
                case 'owner-sshpass':
                    sshuser = owner_user_name;
                    sshpass = get_run_sshpass(pipe_details, auth_key);
                    break
                default:
                    sshuser = 'root';
                    sshpass = get_run_sshpass(pipe_details, auth_key);
                    break
            }
            term = pty.spawn('sshpass', ['-p', sshpass, 'ssh', sshuser + '@' + sshhost, '-p', sshport, '-o', 'StrictHostKeyChecking=no', '-o', 'GlobalKnownHostsFile=/dev/null', '-o', 'UserKnownHostsFile=/dev/null', '-q'], {
                    name: 'xterm-256color',
                    cols: 80,
                    rows: 30,
                    env: { [ENV_TAG_RUNID_NAME]: pipeline_id }
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
    const currentUserIsAdmin = current_user &&
        (
            current_user.admin ||
            (current_user.roles || [])
                .map(role => role.toUpperCase())
                .some(role => CP_MAINTENANCE_SKIP_ROLES.includes(role))
        );

    const onMaintenanceModeChange = (active) => {
        if (active) {
            socket.emit('output', `\r\n\n\u001b[32m${platformName}\u001b[0m is in the \u001b[31mmaintenance mode\u001b[0m.${currentUserIsAdmin ? '' : ' Current session will be closed.'}\n\r\n`);
        } else {
            socket.emit('output', `\r\n\n\u001b[32m${platformName}\u001b[0m is back from the \u001b[31mmaintenance mode\u001b[0m.\n\r\n`);
        }
        if (!currentUserIsAdmin && active) {
            socket.disconnect();
        }
    }

    const stopListeningMaintenanceModeChange = addListener(onMaintenanceModeChange);

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
        stopListeningMaintenanceModeChange();
        console.log((new Date()) + " Disconnecting PID=" + term.pid);
        term.end();
        try {
            process.kill(term.pid);
        }
        catch(ex) {
            console.log(ex);
        }
    });
    socket.emit('term.theme', theme);
})
