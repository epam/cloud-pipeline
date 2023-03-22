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
from random import getrandbits
from threading import RLock
from time import sleep
from . import utils

MAX_RETRY_COUNT = 5
WAITING_DELAY = 3


class AbstractCluster(object):

    def submit_job(self, command, job_name, threads, common_logfile, work_directory, get_output=True,
                   max_retry_count=MAX_RETRY_COUNT):
        pass

    def build_job_command(self, command, job_name, threads, logfile, work_directory, job_identifier):
        pass


class SGECluster(AbstractCluster):
    QSTAT_CMD_TEMPLATE = "qstat -j %s"
    QACCT_CMD_TEMPLATE = "qacct -j %s"
    QSTAT_HEADER_LINES = 2
    IDENTIFIER_ENV_VAR = 'CP_JOB_ID'

    def __init__(self):
        self._lock = RLock()

    def submit_job(self, command, job_name, threads, common_logfile, get_output=True, work_directory=os.getcwd(),
                   max_retry_count=MAX_RETRY_COUNT):
        if not command:
            raise ValueError("The job command is not set.")
        if not job_name:
            print("The job name is not set. The default one will be used.")
        job_identifier = str(getrandbits(64))

        job_logfile = utils.get_log_filename(work_directory, job_name, lock=self._lock, job_identifier=job_identifier)
        job_command = self.build_job_command(command, job_name, threads, job_logfile, work_directory, job_identifier)
        job_exit_code = self._execute_job(job_command, job_identifier, max_retry_count)
        if common_logfile and get_output:
            utils.merge_log(common_logfile, job_logfile, lock=self._lock)
        try:
            os.remove(job_logfile)
        except OSError as e:
            print("Could not delete {file} file.\n".format(file=job_logfile) + e.__str__())
            pass
        return job_exit_code

    def build_job_command(self, command, job_name, threads, logfile, work_directory, job_identifier):
        job_options = "{job_name} -V -v {job_identifier} -j y -R y -o {standard_output_logfile} -pe local {threads} " \
            .format(standard_output_logfile=logfile, job_name="-N " + job_name if job_name else "",
                    threads=threads, job_identifier=self.IDENTIFIER_ENV_VAR + '=' + job_identifier)
        tmp_directory = utils.create_directory(work_directory, "tmp", lock=self._lock)
        bash_script = "{tmp_directory}/{job_name}.sh".format(tmp_directory=tmp_directory,
                                                             job_name=job_name + job_identifier if job_name
                                                             else 'JOB-' + job_identifier)
        with open(bash_script, "w") as file:
            file.write("#!/usr/bin/env bash\n")
            file.write(command + "\n")
        return "qsub {job_options} {bash_script}".format(job_options=job_options, bash_script=bash_script)

    def _execute_job(self, job_command, job_identifier, max_retry_count):
        retry_count = 0
        run_job_result, run_job_error, run_job_exit_code = "", "", ""
        while retry_count < max_retry_count:
            retry_count += 1
            run_job_result, run_job_error, run_job_exit_code = utils.run(job_command)
            sleep(WAITING_DELAY)
            job_id = self._find_job_in_queue(job_identifier)
            if not job_id:
                continue
            return self._find_job(job_id)
        raise RuntimeError('Exceeded retry count ({}) to launch job.\n'
                           'Command "{}" failed to start with exit code: {}\nstdout: {}\nstderr: {}'
                           .format(MAX_RETRY_COUNT, job_command, run_job_exit_code, run_job_result, run_job_error))

    def _find_job_in_queue(self, job_identifier):
        job_ids = self._find_queued_job_ids()
        if not job_ids:
            return None
        for job_id in job_ids:
            qstat_command = self.QSTAT_CMD_TEMPLATE % job_id
            qstat_output, _, exit_code = utils.run(qstat_command)
            if exit_code == 0:
                job_id = self._find_job_number_by_identifier(qstat_output, job_identifier)
                if job_id:
                    return job_id
        return None

    def _find_job(self, job_identifier):
        while True:
            sleep(WAITING_DELAY)
            qacct_command = self.QACCT_CMD_TEMPLATE % job_identifier
            qacct_output, _, exit_code = utils.run(qacct_command)
            if exit_code == 0:
                break
        job_exit_status = self._parse_job_exit_status(qacct_output)
        if job_exit_status is None:
            raise RuntimeError("Failed to determine job exit code for job '{}'".format(job_identifier))
        print("Job {} finished with exit code {}".format(job_identifier, job_exit_status))
        return job_exit_status

    def _find_queued_job_ids(self):
        output, _, exit_code = utils.run('qstat')
        if exit_code != 0:
            return None
        if not output:
            return None
        output_lines = output.splitlines()
        if len(output_lines) < self.QSTAT_HEADER_LINES:
            return None
        ids = []
        for line in output_lines[self.QSTAT_HEADER_LINES:]:
            line = line.strip()
            parts = line.split()
            if len(parts) > 1:
                ids.append(parts[0])
        return ids if len(ids) > 0 else None

    def _find_job_number_by_identifier(self, qstat_output, job_identifier):
        env_list = self._parse_env_vars(qstat_output)
        if not env_list:
            return None
        for env_var in env_list.split(','):
            env_var = env_var.strip()
            if env_var == '{}={}'.format(self.IDENTIFIER_ENV_VAR, job_identifier):
                return self._parse_job_number(qstat_output)
        return None

    @staticmethod
    def _parse_env_vars(qstat_output):
        if not qstat_output:
            return None
        for line in qstat_output.splitlines():
            line = line.strip()
            if line.startswith('env_list:'):
                line = line[len('env_list:'):]
                return line.strip()
        return None

    @staticmethod
    def _parse_job_number(qstat_output):
        if not qstat_output:
            return None
        for line in qstat_output.splitlines():
            line = line.strip()
            if line.startswith('job_number:'):
                parts = line.split()
                if len(parts) != 2:
                    return None
                return parts[1]
        return None

    @staticmethod
    def _parse_job_exit_status(output):
        if not output:
            return None
        for line in output.splitlines():
            line = line.strip()
            if line.strip().startswith('exit_status'):
                parts = line.split()
                if len(parts) != 2:
                    return None
                return int(parts[1])
        return None
