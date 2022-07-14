# Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import os
import subprocess
from random import getrandbits


def get_log_filename(work_directory, job_name, lock):
    logs_directory = create_directory(work_directory, "logs", lock=lock)
    random_number = getrandbits(64)
    return os.path.join(logs_directory, "{job_name}_{number}_out.log"
                        .format(number=random_number, job_name=job_name if job_name else random_number))


def merge_log(common_logfile, job_logfile, lock):
    piece_size = 4096
    with lock:
        with open(common_logfile, 'ab+') as common_logfile, open(job_logfile, 'rb') as job_logfile:
            while True:
                piece = job_logfile.read(piece_size)
                if piece == b'':
                    common_logfile.write("\n".encode())
                    break
                common_logfile.write(piece)



def create_directory(path, name, lock):
    directory = os.path.join(path, name)
    with lock:
        if not os.path.exists(directory):
            os.makedirs(directory, exist_ok=True)
    return directory


def run(job_command, get_output=True, env=None):
    if get_output:
        process = subprocess.Popen(job_command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=env)
    else:
        process = subprocess.Popen(job_command, shell=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, env=env)
    stdout, stderr = process.communicate()
    exit_code = process.wait()
    if exit_code != 0:
        raise RuntimeError('Command "{}" exited with return code: {}, stdout: {}, stderr: {}'
                           .format(job_command, exit_code, stdout, stderr))
    return stdout
