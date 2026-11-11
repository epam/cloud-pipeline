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

# Covers the SLURM master setup of workflows/pipe-common/shell/slurm_setup_master: the MUNGE key it leaves in the
# shared folder, and the configuration its queues get. Runs the script with bash, with the absolute paths it writes to
# replaced by temporary ones. The SLURM daemons, chown, su, nohup, the pipe_log_* commands, util_get_pod_ip,
# slurm_setup_queue and the Python of the run are replaced by stubs, which record their calls. The dd stub records the
# mode of the file it writes to, then runs the real dd. Needs Linux (/proc), GNU stat and nproc. One test gives the
# shared folder the default ACL of a run (create_sys_dir of launch.sh): it needs Python 3 and a file system with
# POSIX ACLs.

import os
import stat
import struct
import subprocess

import pytest

SHELL_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'shell')
SLURM_SETUP_MASTER = os.path.join(SHELL_DIR, 'slurm_setup_master')

RECORDING_STUB = '''#!/bin/bash
printf '%s\\n' "$(basename "$0") $*" >> "$CALLS"
'''

# dd if=/dev/urandom bs=1 count=1024 > <file>: the mode of the file while the key is written into it. The file is
# the stdout of the stub, not of the command substitution, which is a pipe
DD_STUB = RECORDING_STUB + '''printf 'dd mode %s\\n' "$(stat -L -c %a /proc/$$/fd/1)" >> "$CALLS"
exec /bin/dd "$@"
'''

QUEUE_PROFILE = '''export CP_CAP_SLURM_QUEUE_NAME="{queue}"
export CP_CAP_SLURM_QUEUE_STATIC="{static}"
'''

# setfacl -d -m user::rwx -m group::rwx -m other::rx: the extended attribute it sets
_ACL_UNDEFINED_ID = 0xFFFFFFFF
DEFAULT_ACL = struct.pack('<I', 2) + b''.join(struct.pack('<HHI', tag, perm, _ACL_UNDEFINED_ID)
                                              for tag, perm in ((0x01, 7), (0x04, 7), (0x20, 5)))


def _stub(directory, name, body):
    stub = directory.join(name)
    stub.write(body)
    os.chmod(str(stub), stat.S_IRWXU)


@pytest.fixture
def shared_folder(tmpdir):
    return tmpdir.mkdir('shared')


@pytest.fixture
def slurm_setup_master(tmpdir, shared_folder):
    stub_bin = tmpdir.mkdir('stub-bin')
    for name in ('pipe_log_info', 'pipe_log_success', 'pipe_log_fail', 'chown', 'su', 'slurmctld', 'slurmd',
                 'sinfo', 'nohup', 'slurm_setup_queue', 'python'):
        _stub(stub_bin, name, RECORDING_STUB)
    _stub(stub_bin, 'dd', DD_STUB)
    _stub(stub_bin, 'util_get_pod_ip', '#!/bin/bash\necho 10.0.0.1\n')
    _stub(stub_bin, 'check_cp_cap', '#!/bin/bash\nexec bash "%s" "$@"\n' % os.path.join(SHELL_DIR, 'check_cp_cap'))
    for folder in ('etc/slurm', 'munge', 'spool', 'log'):
        tmpdir.ensure(folder, dir=True)
    scripts_dir = tmpdir.mkdir('cap-scripts')
    scripts_dir.join('slurm_profile_main.q_queue.sh').write(QUEUE_PROFILE.format(queue='main.q', static='true'))
    scripts_dir.join('slurm_profile_gpu.q_queue.sh').write(QUEUE_PROFILE.format(queue='gpu.q', static='false'))
    calls = tmpdir.join('calls')
    with open(SLURM_SETUP_MASTER) as f:
        script = f.read() \
            .replace('ls /etc/ ', 'ls %s/ ' % tmpdir.join('etc')) \
            .replace('"/etc/$_SLURM_CONFIG_DIR"', '"%s/$_SLURM_CONFIG_DIR"' % tmpdir.join('etc')) \
            .replace('/etc/munge/', str(tmpdir.join('munge')) + '/') \
            .replace('/var/spool/', str(tmpdir.join('spool')) + '/') \
            .replace('/var/log/', str(tmpdir.join('log')) + '/')

    def run(resumed=False):
        env = {
            'PATH': str(stub_bin) + os.pathsep + os.environ['PATH'],
            'CALLS': str(calls),
            'SHARED_FOLDER': str(shared_folder),
            'CP_CAP_SLURM_PREINSTALLED': 'true',
            'RESUMED_RUN': 'true' if resumed else 'false',
            'CP_CAP_SCRIPTS_DIR': str(scripts_dir),
            'CP_PYTHON2_PATH': str(stub_bin.join('python')),
            'COMMON_REPO_DIR': str(tmpdir.join('pipe-common')),
            'LOG_DIR': str(tmpdir.join('log'))
        }
        exit_code = subprocess.call(['bash', '-c', 'umask 022\n' + script], env=env)
        return exit_code, calls.read().splitlines() if calls.exists() else []

    return run


def _assert_the_key_is_readable_by_root_only(shared_folder, calls):
    key = shared_folder.join('.slurm', 'munge.key')
    assert stat.S_IMODE(os.stat(str(key)).st_mode) == 0o600
    assert key.size() == 1024
    # Before its mode is set, the key is not written to a file that the other users may open
    assert [call for call in calls if call.startswith('dd mode ')] == ['dd mode 600']
    assert not [name for name in os.listdir(str(shared_folder.join('.slurm'))) if name.startswith('.munge.key')]


def test_munge_key_in_the_shared_folder_is_readable_by_root_only(slurm_setup_master, shared_folder, tmpdir):
    # The key stays there for the workers that join later. Anyone who can read it can forge MUNGE credentials
    exit_code, calls = slurm_setup_master()

    assert exit_code == 0
    _assert_the_key_is_readable_by_root_only(shared_folder, calls)
    assert tmpdir.join('munge', 'munge.key').read_binary() == shared_folder.join('.slurm', 'munge.key').read_binary()


def test_munge_key_is_readable_by_root_only_with_the_default_acl_of_the_shared_folder(slurm_setup_master,
                                                                                         shared_folder):
    # With a default ACL, a new file gets its permissions whatever the umask: other::r for the file of a redirect
    if not hasattr(os, 'setxattr'):
        pytest.skip('Setting a default ACL needs os.setxattr (Python 3 on Linux)')
    try:
        os.setxattr(str(shared_folder), 'system.posix_acl_default', DEFAULT_ACL)
    except OSError as e:
        pytest.skip('The file system does not support POSIX ACLs: %s' % e)

    exit_code, calls = slurm_setup_master()

    assert exit_code == 0
    _assert_the_key_is_readable_by_root_only(shared_folder, calls)


def test_queues_get_the_slurm_config_of_the_shared_folder(slurm_setup_master, shared_folder):
    # slurm_setup_queue uses /common/.slurm/slurm.conf if it gets no config
    exit_code, calls = slurm_setup_master(resumed=True)

    assert exit_code == 0
    assert [call for call in calls if call.startswith('slurm_setup_queue')] == [
        'slurm_setup_queue %s' % shared_folder.join('.slurm', 'slurm.conf')]
