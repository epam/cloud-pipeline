var term;
var socket = io(location.origin, {path: '/ssh/socket.io'})
var buf = '';

function Wetty(argv) {
    this.argv_ = argv;
    this.io = null;
    this.pid_ = -1;
}

Wetty.prototype.run = function() {
    this.io = this.argv_.io.push();

    this.io.onVTKeystroke = this.sendString_.bind(this);
    this.io.sendString = this.sendString_.bind(this);
    this.io.onTerminalResize = this.onTerminalResize.bind(this);
}

Wetty.prototype.sendString_ = function(str) {
    socket.emit('input', str);
};

Wetty.prototype.onTerminalResize = function(col, row) {
    socket.emit('resize', { col: col, row: row });
};

var theme = 'default';
var generalPreferences = {
  "ctrl-c-copy": true,
  "ctrl-v-paste": true,
  "use-default-window-copy": true
};
var themes = {
    light: Object.assign(
      generalPreferences, {
        "background-color": "#fafafa",
        "foreground-color": "#333333",
        "cursor-color": "rgba(50, 50, 50, 0.5)",
        "color-palette-overrides": { 51: 'rgb(0, 140, 140)'}
    }),
    default: Object.assign(
      generalPreferences, {
        "background-color": "rgb(16, 16, 16)",
        "foreground-color": "rgb(240, 240, 240)",
        "cursor-color": "rgba(255, 0, 0, 0.5)",
        "color-palette-overrides": null
    })
};

function initializeTermThemes() {
    if (term) {
        term.setProfile('default');
        term.prefs_.importFromJson(themes.default);
        term.setProfile('light');
        term.prefs_.importFromJson(themes.light);
        term.setProfile('default');
    }
}

function setTerminalTheme() {
    if (term) {
        var currentThemeName = 'default';
        if (theme && theme.toLowerCase() == 'light') {
            currentThemeName = 'light';
        }
        term.setProfile(currentThemeName);
    }
}

function toggleTheme(event) {
    if (event) {
        event.stopPropagation();
    }
    if (theme == 'default') {
        theme = 'light';
    } else {
        theme = 'default';
    }
    setTerminalTheme();
    var terminalDiv = document.getElementById('terminal');
    if (terminalDiv) {
        terminalDiv.focus();
    }
}

document.addEventListener('DOMContentLoaded', function() {
    var settingsBtn = document.getElementById('settings');
    if (settingsBtn) {
        settingsBtn.addEventListener('click', toggleTheme);
    }
});

socket.on('connect', function() {
    lib.init(function() {
        hterm.defaultStorage = new lib.Storage.Local();
        term = new hterm.Terminal();
        initializeTermThemes();
        window.term = term;
        term.decorate(document.getElementById('terminal'));

        term.setCursorPosition(0, 0);
        term.setCursorVisible(true);

        term.runCommandClass(Wetty, document.location.hash.substr(1));
        socket.emit('resize', {
            col: term.screenSize.width,
            row: term.screenSize.height
        });

        if (buf && buf != '')
        {
            term.io.writeUTF16(buf);
            buf = '';
        }
        setTerminalTheme();
    });
});

socket.on('output', function(data) {
    if (!term) {
        buf += data;
        return;
    }
    term.io.writeUTF16(data);
});

socket.on('disconnect', function() {
    console.log("Socket.io connection closed");
});

socket.on('term.theme', function(sshTheme) {
    theme = sshTheme;
    setTerminalTheme();
});
