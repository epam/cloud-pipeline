var express = require('express');
var http = require('http');
var path = require('path');
var server = require('socket.io');
var pty = require('pty.js');
var request = require("sync-request");
var child_process = require('child_process');

const checkMaintenanceMode = require('./maintenance');

// Constants
const ENV_TAG_RUNID_NAME = 'CP_ENV_TAG_RUNID';
const CONN_QUOTA_PER_RUN_ID = process.env.CP_EDGE_MAX_SSH_CONNECTIONS || 25;

const CP_MAINTENANCE_SKIP_ROLES = (process.env.CP_MAINTENANCE_SKIP_ROLES || '')
    .split(',')
    .map(o => o.trim().toUpperCase())
    .filter(o => o.length);

function console_log(message) {
    console.log((new Date()) + ' ' + message);
}

function console_error(message) {
    console.error((new Date()) + ' ' + message);
}

function call_api(api_method, auth_key, httpMethod = 'GET', payload = undefined) {
    const options = {
        'headers': {
            'Authorization': 'Bearer ' + auth_key
        },
        json: payload
    };
    const response = request(httpMethod, process.env.API + api_method, options);
    if (response.statusCode == 200) {
        return JSON.parse(response.getBody()).payload;
    }
    return null;
}

function load_run(run_id, auth_key) {
    return call_api('/run/' + run_id, auth_key);
}

function load_runs(pretty_url_path, auth_key) {
    return call_api(
        `/run/prettyUrl?url=${pretty_url_path}`,
         auth_key
    );
}

function extract_run_details(payload) {
    if (!payload) {
        return null;
    }
    const parameters = payload.pipelineRunParameters
        ? payload.pipelineRunParameters.reduce(function(parameters, parameter) {
            parameters[parameter.name] = parameter.value;
            return parameters;
        }, {})
        : {};
    return {
        'id': payload.id,
        'ip': payload.podIP,
        'pass': payload.sshPassword,
        'owner': payload.owner,
        'pod_id': payload.podId,
        'platform': payload.platform,
        'parameters': parameters
    };
}

function get_run_details(run_id, auth_key) {
    const payload = load_run(run_id, auth_key);
    return extract_run_details(payload);
}

function get_run_details_by_pretty_url_path(pretty_url_path, auth_key) {
    const run = load_runs(pretty_url_path, auth_key);
    if (run && run.prettyUrl !== undefined) {
        return extract_run_details(run);
    }
    return null;
}

function get_run_id(referer, auth_key) {
    if (!referer) {
        return null;
    }
    if (match = referer.match('/ssh/pipeline/(\\d+)$')) {
        const run_id = match[1];
        if (!run_id) {
            console_log('Could not find run id in referer url: ' + referer + '.');
            return null;
        }

        return run_id;
    } else if (match = referer.match('/ssh/pipeline/(.+)$')) {
        const run_pretty_url_path = match[1];
        if (!run_pretty_url_path) {
            console_log('Could not find ssh pretty url in referer url: ' + referer + '.');
            return null;
        }

        const run_details = get_run_details_by_pretty_url_path(run_pretty_url_path, auth_key);
        if (!run_details) {
            console_log('Could not find run with ' + run_pretty_url_path + ' ssh pretty url.');
            return null;
        }

        return run_details.id;
    } else {
        console_log('Could not find run id or ssh pretty url in referer url: ' + referer + '.');
        return null;
    }
}

function get_current_user(auth_key) {
    const payload = call_api('/whoami', auth_key);
    if (payload) {
        return {
            'id': payload.id,
            'name': payload.userName,
            'admin': payload.admin,
            'roles': (payload.groups || []).concat((payload.roles || []).map(function (role) { return role.name; }))
        };
    }
    return null;
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

function get_user_metadata(user_id, auth_key) {
    const payload = call_api(
        '/metadata/load',
         auth_key,
        'POST',
        [{
            entityClass: 'PIPELINE_USER',
            entityId: user_id
        }]
    );
    if (payload && payload.length) {
        const {data = {}} = payload[0];
        const metadata = {};
        for (let key in data) {
            metadata[key] = data[key].value;
        }
        return metadata;
    }
    return {};
}

function get_boolean(value) {
    return value ? value.toLowerCase().trim() === 'true' : false;
}

function get_preference(preference, auth_key) {
    const payload = call_api('/preferences/' + preference, auth_key);
    return payload && payload.value;
}

function get_boolean_preference(preference, auth_key) {
    return get_boolean(get_preference(preference, auth_key));
}

function conn_quota_available(run_id) {
    // This command:
    // 1. Searches for the processes with "ssh" command (grep -wl ssh /proc/*/comm) and prints the file name (e.g. /proc/123/comm)
    // 2. Replaces the "comm" with "environ" file name (it will be searched for a "tag")
    // 3. Searches the resulting list of "ssh" processes, that are "tagged" with the RUN ID
    var running_pids_command = 'ssh_pids=$(grep -wl ssh /proc/*/comm | sed -e "s/comm/environ/g") && \
                                [ "$ssh_pids" ] && \
                                grep -l "\\b' + ENV_TAG_RUNID_NAME + '=' + run_id + '\\b" $ssh_pids';

    let running_pids_count = 0;
    try {
        const stdout = child_process.execSync(running_pids_command).toString();
        running_pids_count = stdout.split(/\r\n|\r|\n/).filter(item => item).length;
    }
    catch (err) {
        // (1) means that there is no match (i.e. no ssh processes with the correct "tag")
        // This is totally ok behavior and we consider that quota IS available
        // https://www.unix.com/man-page/posix/1P/grep/
        if (err.status != 1) {
            // If something went wrong - we'll be optimists and allow the connection, but drop a log line on the error
            console_log('Cannot get running connections for a run #' + run_id + ' (exit code: ' + err.status + ', stderr: ' + err.stderr + ')');
        }
    }

    console_log('Already running "' + running_pids_count + '/' + CONN_QUOTA_PER_RUN_ID + '" PIDs for run #' + run_id);
    return running_pids_count < CONN_QUOTA_PER_RUN_ID;
}

function get_owner_user_name(owner) {
    // split owner by @ in case it represented by email address
    return owner.split("@")[0];
}

function get_run_sshpass(run_details, auth_key) {
    const parent_run_id = run_details.parameters['parent-id'];
    const run_shared_users_enabled = get_boolean(run_details.parameters['CP_CAP_SHARE_USERS']);
    if (run_shared_users_enabled && parent_run_id) {
        const parent_run_details = get_run_details(parent_run_id, auth_key);
        return parent_run_details.pass;
    } else {
        return run_details.pass;
    }
}

function resolve_user_name(user_name, user_name_case) {
    if (!user_name) {
        return null;
    }
    switch (user_name_case) {
        case 'lower':
            return user_name.toLowerCase();
        case 'upper':
            return user_name.toUpperCase();
        default:
            return user_name;
    }
}

const opts = require('optimist')
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

const sshport = opts.sshport ? opts.sshport : 22;

process.on('uncaughtException', function(e) {
    console_error('Error: ' + e);
    if (e.stack) {
        console_error('Error: ' + e.stack);
    }
});

const app = express();
app.get('/ssh/pipeline/*', function(req, res) {
    res.sendfile(__dirname + '/public/ssh/index.html');
});
app.use('/', express.static(path.join(__dirname, 'public')));


const httpserv = http.createServer(app).listen(opts.port, function() {
    console_log('http on port ' + opts.port);
});

const {addListener} = checkMaintenanceMode();

const io = server(httpserv,{path: '/ssh/socket.io'});
io.on('connection', function(socket) {
    const auth_key = socket.handshake.headers['token'];
    const referer = socket.request.headers.referer;
    console_log('Connection accepted for ' + referer);

    const run_id = get_run_id(referer, auth_key)
    if (!run_id) {
        console_log('Aborting SSH connection because run id have not been found for ' + referer + '...');
        socket.disconnect();
        return;
    }

    if (!conn_quota_available(run_id)) {
        const conn_err_msg = 'Aborting SSH connection because quota of ' + CONN_QUOTA_PER_RUN_ID + ' connections '
                             + 'exceeded for run #' + run_id + '...';
        console_log(conn_err_msg);
        socket.emit('output', conn_err_msg);
        socket.disconnect();
        return;
    }

    const run_details = get_run_details(run_id, auth_key);
    if (!run_details) {
        console_log('Aborting SSH connection because details have not been found for run #' + run_id + '...');
        socket.disconnect();
        return;
    }

    if (!run_details.id || !run_details.ip || !run_details.pass || !run_details.owner) {
        console_log('Aborting SSH connection because id/ip/pass/owner have not been found '
                    + 'for run #' + run_id + '...');
        socket.disconnect();
        return;
    }

    const sshhost = run_details.ip;
    const owner_user_name = get_owner_user_name(run_details.owner);
    let run_ssh_mode = run_details.parameters['CP_CAP_SSH_MODE'];
    if (!run_ssh_mode) {
        if (run_details.platform == 'windows') {
            run_ssh_mode = 'owner-sshpass';
        } else {
            run_ssh_mode = get_boolean_preference('system.ssh.default.root.user.enabled', auth_key) ? 'root' : 'owner';
        }
    }
    const run_user_name_case = run_details.parameters['CP_CAP_USER_NAME_CASE'] || 'default';
    const run_user_name_metadata_key = run_details.parameters['CP_CAP_USER_NAME_METADATA_KEY'] || 'local_user_name';
    const current_user = get_current_user(auth_key);
    const current_user_metadata = current_user ? get_user_metadata(current_user.id, auth_key) : {};
    const current_user_login_name = current_user ? current_user.name : undefined;
    const current_user_local_name = current_user_metadata[run_user_name_metadata_key];
    const current_user_name = current_user_local_name
        || (current_user_login_name ? resolve_user_name(current_user_login_name, run_user_name_case) : undefined);
    const platformName = get_platform_name(auth_key);
    const theme = current_user_metadata['ui.ssh.theme'] || get_ssh_theme(auth_key) || 'default';
    let sshuser;
    let sshpass;
    switch (run_ssh_mode) {
        case 'user':
            sshuser = current_user_name;
            sshpass = sshuser;
            break
        case 'owner':
            sshuser = owner_user_name;
            sshpass = sshuser;
            break
        case 'owner-sshpass':
            sshuser = owner_user_name;
            sshpass = get_run_sshpass(run_details, auth_key);
            break
        default:
            sshuser = 'root';
            sshpass = get_run_sshpass(run_details, auth_key);
            break
    }
    console_log('Establishing SSH connection to run #' + run_id + ' ' +
                'as ' + sshuser + ' (' + current_user_login_name + ') ' +
                'in ' + run_ssh_mode + ' mode...');
    const term = pty.spawn('sshpass', ['-p', sshpass, 'ssh', sshuser + '@' + sshhost,
                                       '-p', sshport,
                                       '-o', 'StrictHostKeyChecking=no',
                                       '-o', 'GlobalKnownHostsFile=/dev/null',
                                       '-o', 'UserKnownHostsFile=/dev/null',
                                       '-q'], {
            name: 'xterm-256color',
            cols: 80,
            rows: 30,
            env: { [ENV_TAG_RUNID_NAME]: run_id }
    });

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
            console_log('Aborting SSH connection because platform maintenance has started for run #' + run_id + '...');
            socket.disconnect();
        }
    }

    const stopListeningMaintenanceModeChange = addListener(onMaintenanceModeChange);

    console_log("PID=" + term.pid + " STARTED to IP=" + sshhost + ", RUNNO=" + run_id + " on behalf of user=" + sshuser);
    term.on('data', function(data) {
        socket.emit('output', data);
    });
    term.on('exit', function(code) {
        console_log("PID=" + term.pid + " ENDED")
    });
    socket.on('resize', function(data) {
        term.resize(data.col, data.row);
    });
    socket.on('input', function(data) {
        term.write(data);
    });
    socket.on('disconnect', function() {
        stopListeningMaintenanceModeChange();
        console_log("Disconnecting PID=" + term.pid);
        term.end();
        try {
            process.kill(term.pid);
        }
        catch(ex) {
            console_log('Error: ' + ex);
        }
    });
    socket.emit('term.theme', theme);
})
