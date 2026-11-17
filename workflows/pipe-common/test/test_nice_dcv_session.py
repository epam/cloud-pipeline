# Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Covers the Xfce desktop of a NICE DCV session, set up by workflows/pipe-common/shell/nice_dcv_setup:
# configure_dcv_extras copies the Xfce templates of workflows/pipe-common/resources/linux/xfce and generates the
# xfce4launch session script, with the keyboard settings of the run baked in. The functions of xfce4launch then apply
# the panel (with or without the keyboard layout switcher), drop the wallpaper and set the keyboard layouts.
# Also covers the main part of nice_dcv_setup: the desktop is set up after an install of DCV, and in an image that has
# DCV installed already, but not after a failed install.
# Runs configure_dcv_extras with bash into a temporary folder, then the functions of the generated script one by one,
# without its main part, so nothing is started in the background. xfconf-query, xrandr, xfdesktop, setxkbmap, pgrep,
# sleep and the pipe_log_* commands are replaced by stubs, which record their calls. The xfce4-xkb-plugin folders of
# the generated script are pointed to a temporary folder, to tell whether the plugin is installed. The main part of
# nice_dcv_setup runs with install_prerequisites, install_dcv, configure_dcv, configure_dcv_extras and run_dcv
# replaced by stubs.

import os
import subprocess

import pytest

PIPE_COMMON_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NICE_DCV_SETUP = os.path.join(PIPE_COMMON_DIR, 'shell', 'nice_dcv_setup')

XKB_PLUGIN_DIRS = '/usr/share/xfce4/panel/plugins /usr/share/xfce4/panel-plugins'

LOG_STUBS = r'''
record() { printf '%s\n' "$*" >> "$CALLS"; }
pipe_log_info() { record "info $1"; }
pipe_log_warn() { record "warn $1"; }
pipe_log_fail() { record "fail $1"; }
'''

SESSION_STUBS = r'''
record() { printf '%s\n' "$*" >> "$CALLS"; }
sleep() { :; }
pgrep() { [ "$STUB_XFCE_RUNNING" == "true" ]; }
xfdesktop() { record "xfdesktop $*"; }
xrandr() { printf '%s\n' "$STUB_XRANDR"; }
setxkbmap() { record "setxkbmap $*"; }
xfconf-query() {
    case " $* " in
        *" -l "*) printf '%s\n' "$STUB_XFCONF_LIST" ;;
        *) record "xfconf-query $*" ;;
    esac
}
'''


def _configure_dcv_extras_source():
    with open(NICE_DCV_SETUP) as f:
        text = f.read()
    start = text.index('function configure_dcv_extras {')
    # The function writes the session script with heredocs, which have functions of their own
    end = text.index('\n}\n', text.index('chmod +x "$CP_DCV_EXTRAS_DIR/xfce4launch"')) + 3
    return text[start:end]


def _generate(tmpdir, common_repo_dir=PIPE_COMMON_DIR, **env_vars):
    calls = tmpdir.join('calls')
    calls.write('')
    extras_dir = tmpdir.join('extras')
    script = LOG_STUBS + \
        'CP_DCV_EXTRAS_DIR="%s"\n' % extras_dir + \
        'CP_DCV_XFCE_TEMPLATES_DIR="$CP_DCV_EXTRAS_DIR/xfce"\n' + \
        _configure_dcv_extras_source() + \
        'configure_dcv_extras\necho "result $?" >> "$CALLS"\n'
    env = _env(CALLS=str(calls), COMMON_REPO_DIR=str(common_repo_dir), **env_vars)
    subprocess.check_call(['bash', '-c', script], env=env)
    return extras_dir, calls.read().splitlines()


def _env(**env_vars):
    env = dict(os.environ)
    for name in list(env):
        if name.startswith('CP_DCV_') or name.startswith('STUB_'):
            env.pop(name)
    env.update(env_vars)
    return env


def _session_definitions(extras_dir, plugins_dir):
    with open(str(extras_dir.join('xfce4launch'))) as f:
        text = f.read()
    assert XKB_PLUGIN_DIRS in text
    text = text.replace(XKB_PLUGIN_DIRS, str(plugins_dir))
    return text[:text.index('if [ "${1:-}" == "--runtime" ]; then')]


def _session(tmpdir, extras_dir, call, plugin=None, **env_vars):
    calls = tmpdir.join('session-calls')
    calls.write('')
    home = tmpdir.join('home')
    home.ensure(dir=True)
    plugins_dir = tmpdir.join('plugins')
    plugins_dir.ensure(dir=True)
    if plugin:
        plugins_dir.join(plugin + '.desktop').write('')
    script = SESSION_STUBS + _session_definitions(extras_dir, plugins_dir) + call + '\n'
    env = _env(CALLS=str(calls), HOME=str(home), **env_vars)
    output = subprocess.check_output(['bash', '-c', script], env=env, universal_newlines=True)
    return home, calls.read().splitlines(), output


def test_extras_get_the_xfce_templates_and_an_executable_session_script(tmpdir):
    extras_dir, calls = _generate(tmpdir)

    assert 'result 0' in calls
    assert not any(call.startswith('warn ') or call.startswith('fail ') for call in calls)
    for name in ('xfce4-panel.xml', 'keyboard-layout.xml', 'xkb-plugin.rc'):
        assert extras_dir.join('xfce', name).check(file=True)
    assert os.access(str(extras_dir.join('xfce4launch')), os.X_OK)


def test_session_script_has_the_default_keyboard_settings(tmpdir):
    extras_dir, _ = _generate(tmpdir)

    text = extras_dir.join('xfce4launch').read()
    assert 'CP_DCV_XKB_LAYOUTS="us,fr,de"' in text
    assert 'CP_DCV_XKB_MODEL="pc105"' in text
    assert 'CP_DCV_XKB_TOGGLE="grp:ctrl_shift_toggle"' in text
    assert 'CP_DCV_XFCE_TEMPLATES_DIR="%s"' % extras_dir.join('xfce') in text


def test_session_script_has_the_keyboard_settings_of_the_run(tmpdir):
    extras_dir, _ = _generate(tmpdir, CP_DCV_XKB_LAYOUTS='us,ru', CP_DCV_XKB_MODEL='pc104',
                              CP_DCV_XKB_TOGGLE='grp:alt_shift_toggle')

    text = extras_dir.join('xfce4launch').read()
    assert 'CP_DCV_XKB_LAYOUTS="us,ru"' in text
    assert 'CP_DCV_XKB_MODEL="pc104"' in text
    assert 'CP_DCV_XKB_TOGGLE="grp:alt_shift_toggle"' in text


def test_extras_warn_without_the_xfce_templates(tmpdir):
    extras_dir, calls = _generate(tmpdir, common_repo_dir=tmpdir.mkdir('empty-repo'))

    assert any(call.startswith('warn [WARNING] Xfce configuration templates are not found') for call in calls)
    assert os.access(str(extras_dir.join('xfce4launch')), os.X_OK)
    assert 'result 0' in calls


@pytest.mark.parametrize('plugin', ['xkb-plugin', 'xkb'])
def test_session_panel_has_the_layout_switcher_if_the_plugin_is_installed(tmpdir, plugin):
    extras_dir, _ = _generate(tmpdir)
    home, _, _ = _session(tmpdir, extras_dir, 'configure_xfce', plugin=plugin)

    xfconf_dir = home.join('.config', 'xfce4', 'xfconf', 'xfce-perchannel-xml')
    panel = xfconf_dir.join('xfce4-panel.xml').read()
    assert '_XKB_PLUGIN_NAME_' not in panel
    assert 'value="%s"' % plugin in panel
    assert home.join('.config', 'xfce4', 'panel', '%s-6.rc' % plugin).check(file=True)
    layout = xfconf_dir.join('keyboard-layout.xml').read()
    assert 'value="us,fr,de"' in layout
    assert 'value=",,"' in layout
    assert 'value="grp:ctrl_shift_toggle"' in layout


def test_session_panel_has_no_layout_switcher_without_the_plugin(tmpdir):
    extras_dir, _ = _generate(tmpdir)
    home, _, output = _session(tmpdir, extras_dir, 'configure_xfce')

    xfconf_dir = home.join('.config', 'xfce4', 'xfconf', 'xfce-perchannel-xml')
    panel = xfconf_dir.join('xfce4-panel.xml').read()
    assert '<!--XKB-->' not in panel
    assert 'name="plugin-6"' not in panel
    assert '<value type="int" value="6"/>' not in panel
    assert 'value="_XKB_PLUGIN_NAME_"' not in panel
    assert not xfconf_dir.join('keyboard-layout.xml').check()
    assert 'Keyboard layout switcher (xfce4-xkb-plugin) is not installed' in output


def test_session_drops_the_wallpaper_of_each_registered_monitor(tmpdir):
    extras_dir, _ = _generate(tmpdir)
    _, calls, _ = _session(tmpdir, extras_dir, 'disable_desktop_backdrop',
                           STUB_XFCONF_LIST='/backdrop/screen0/monitorVNC-0/workspace0/last-image\n'
                                            '/backdrop/screen0/monitorVNC-0/workspace1/last-image\n'
                                            '/backdrop/single-workspace-mode')

    assert calls == [
        'xfconf-query -c xfce4-desktop -p /backdrop/single-workspace-mode -n -t bool -s true',
        'xfconf-query -c xfce4-desktop -p /backdrop/screen0/monitorVNC-0/workspace0/image-style -n -t int -s 0',
        'xfconf-query -c xfce4-desktop -p /backdrop/screen0/monitorVNC-0/workspace0/last-image -n -t string -s ',
        'xfdesktop --reload',
    ]


def test_session_drops_the_wallpaper_of_the_randr_outputs_if_no_monitor_is_registered(tmpdir):
    extras_dir, _ = _generate(tmpdir, CP_DCV_XFCE_STARTUP_TIMEOUT='2')
    _, calls, _ = _session(tmpdir, extras_dir, 'disable_desktop_backdrop',
                           STUB_XFCONF_LIST='', STUB_XRANDR='DUMMY0 connected primary 1920x1080+0+0\n'
                                                            'DUMMY1 disconnected')

    assert 'xfconf-query -c xfce4-desktop -p /backdrop/screen0/monitorDUMMY0/workspace0/image-style ' \
           '-n -t int -s 0' in calls
    assert not any('monitorDUMMY1' in call for call in calls)


def test_session_sets_the_keyboard_layouts(tmpdir):
    extras_dir, _ = _generate(tmpdir)
    _, calls, _ = _session(tmpdir, extras_dir, 'configure_xfce && configure_keyboard_layout', plugin='xkb-plugin')

    assert calls == ['setxkbmap -model pc105 -layout us,fr,de -option grp:ctrl_shift_toggle']


def test_session_keeps_one_layout_without_the_plugin(tmpdir):
    extras_dir, _ = _generate(tmpdir)
    _, calls, _ = _session(tmpdir, extras_dir, 'configure_xfce && configure_keyboard_layout')

    assert calls == []


@pytest.mark.parametrize('running, expected', [('true', 0), ('false', 1)])
def test_session_waits_for_xfdesktop(tmpdir, running, expected):
    extras_dir, _ = _generate(tmpdir, CP_DCV_XFCE_STARTUP_TIMEOUT='3')
    _, _, output = _session(tmpdir, extras_dir, 'wait_for_xfce_session; echo "waited $?"',
                            STUB_XFCE_RUNNING=running)

    assert 'waited %d' % expected in output


def test_session_script_replaces_the_one_of_an_earlier_start(tmpdir):
    extras_dir = tmpdir.join('extras')
    extras_dir.ensure(dir=True)
    extras_dir.join('xfce4launch').write('#!/bin/bash\n# generated by an earlier start\nexec startxfce4\n')

    extras_dir, _ = _generate(tmpdir)

    text = extras_dir.join('xfce4launch').read()
    assert 'generated by an earlier start' not in text
    assert text.count('exec startxfce4') == 1


MAIN_STUBS = r'''
record() { printf '%s\n' "$*" >> "$CALLS"; }
pipe_log_info() { record "info $1"; }
pipe_log_fail() { record "fail $1"; }
install_prerequisites() { record install_prerequisites; [ "$STUB_INSTALL" != "fail" ]; }
install_dcv() { record install_dcv; }
configure_dcv() { record configure_dcv; }
configure_dcv_extras() { record configure_dcv_extras; }
run_dcv() { record run_dcv; }
'''


def _main_source():
    with open(NICE_DCV_SETUP) as f:
        text = f.read()
    start = text.index('if [ $IS_DCV_INSTALLED -eq 0 ]; then')
    end = text.index('\nrun_dcv\n', start) + len('\nrun_dcv\n')
    return text[start:end]


def _main(tmpdir, **env_vars):
    calls = tmpdir.join('main-calls')
    calls.write('')
    result = subprocess.call(['bash', '-c', MAIN_STUBS + _main_source()], env=_env(CALLS=str(calls), **env_vars))
    return result, calls.read().splitlines()


def test_desktop_is_set_up_in_an_image_with_dcv_installed(tmpdir):
    result, calls = _main(tmpdir, IS_DCV_INSTALLED='0')

    assert result == 0
    assert calls == ['info --> DCV installed already', 'configure_dcv_extras', 'run_dcv']


def test_desktop_is_set_up_after_dcv_is_installed(tmpdir):
    result, calls = _main(tmpdir, IS_DCV_INSTALLED='1')

    assert result == 0
    assert calls == ['install_prerequisites', 'install_dcv', 'configure_dcv', 'configure_dcv_extras', 'run_dcv']


def test_desktop_is_not_set_up_if_dcv_install_fails(tmpdir):
    result, calls = _main(tmpdir, IS_DCV_INSTALLED='1', STUB_INSTALL='fail')

    assert result == 1
    assert calls == ['install_prerequisites', 'fail [ERROR] DCV installation failed. Exiting']
