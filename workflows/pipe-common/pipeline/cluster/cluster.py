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
from threading import RLock
from . import utils


class AbstractCluster(object):
    def submit_job(self, command, job_name, threads, common_logfile, work_directory, get_output=True):
        pass

    def build_job_command(self, command, job_name, threads, logfile, work_directory):
        pass


class SGECluster(AbstractCluster):

    def __init__(self):
        self._lock = RLock()

    def submit_job(self, command, job_name, threads, common_logfile, get_output=True, work_directory=os.getcwd()):
        if not command:
            raise ValueError("The job command is not set.")
        if not job_name:
            print("The job name is not set. The default one will be used.")
        job_logfile = utils.get_log_filename(work_directory, job_name, lock=self._lock)
        job_command = self.build_job_command(command, job_name, threads, job_logfile, work_directory)
        job_result = utils.run(job_command, get_output=get_output)
        if common_logfile and get_output:
            utils.merge_log(common_logfile, job_logfile, lock=self._lock)
        try:
            os.remove(job_logfile)
        except OSError as e:
            print("Could not delete {file} file.\n".format(file=job_logfile) + e.__str__())
            pass
        return job_result

    def build_job_command(self, command, job_name, threads, logfile, work_directory):
        job_options = "-sync y {job_name} -V -j y -R y -o {standard_output_logfile} -pe local {threads} " \
            .format(standard_output_logfile=logfile, job_name="-N " + job_name if job_name else "",
                    threads=threads)
        tmp_directory = utils.create_directory(work_directory, "tmp", lock=self._lock)
        bash_script = "{tmp_directory}/{job_name}.sh".format(tmp_directory=tmp_directory,
                                                             job_name=job_name + str(getrandbits(64)) if job_name
                                                             else str(getrandbits(64)))
        with open(bash_script, "w") as file:
            file.write("#!/usr/bin/env bash\n")
            file.write(command + "\n")
        return "qsub {job_options} {bash_script}".format(job_options=job_options, bash_script=bash_script)
