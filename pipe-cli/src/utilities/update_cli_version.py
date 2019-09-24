# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import stat
import sys
from abc import ABCMeta
import requests
import platform
import zipfile
import subprocess
import uuid
from datetime import datetime


from src.config import Config


class UpdateCLIVersionManager(object):

    def __init__(self):
        pass

    def update(self, path=None):
        updater = self.get_updater()

        if not path:
            path = updater.build_path_from_api()
        requests.urllib3.disable_warnings()
        if not self.is_downloadable(path):
            raise RuntimeError("Provided url '%s' not downloadable or invalid." % path)

        updater.update_version(path)

    @staticmethod
    def get_updater():
        if platform.system() == 'Windows':
            return WindowsUpdater()
        elif platform.system() == 'Linux':
            return LinuxUpdater()
        else:
            raise RuntimeError("Update operation available for Windows or Linux platforms only")

    @staticmethod
    def is_downloadable(url):
        header = requests.head(url, verify=False, allow_redirects=True).headers
        content_type = header.get('content-type')
        return content_type is None or 'html' not in content_type.lower()


class CLIVersionUpdater:
    __metaclass__ = ABCMeta

    CP_RESTAPI_SUFFIX = 'restapi/'

    def update_version(self, path):
        pass

    def get_download_suffix(self):
        return ""

    def build_path_from_api(self):
        config = Config.instance()
        api_path = str(config.api)
        if not api_path:
            raise RuntimeError("Failed to find Cloud Pipeline CLI download url")
        return api_path.replace(self.CP_RESTAPI_SUFFIX, self.get_download_suffix())


class LinuxUpdater(CLIVersionUpdater):

    def get_download_suffix(self):
        return 'pipe'

    def update_version(self, path):
        path_to_script = os.path.realpath(sys.argv[0])
        self.replace_executable(path_to_script, path)
        self.set_x_permission(path_to_script)

    @staticmethod
    def replace_executable(path_to_script, path):
        os.remove(path_to_script)
        request = requests.get(path, verify=False)
        open(path_to_script, 'wb').write(request.content)

    @staticmethod
    def set_x_permission(path_to_script):
        st = os.stat(path_to_script)
        os.chmod(path_to_script, st.st_mode | stat.S_IEXEC)


class WindowsUpdater(CLIVersionUpdater):
    WINDOWS_SRC_ZIP = 'pipe.zip'
    ATTEMPTS_COUNT = 10
    LOG_FILE = 'update.log'

    def get_download_suffix(self):
        return self.WINDOWS_SRC_ZIP

    def update_version(self, path):
        random_prefix = str(uuid.uuid4()).replace("-", "")

        tmp_folder = self.get_tmp_folder()
        self.check_write_permissions(tmp_folder)

        path_to_src_dir = os.path.dirname(sys.executable)
        self.check_write_permissions(path_to_src_dir)

        tmp_src_dir = self.download_new_src(path, random_prefix)
        path_to_bat = os.path.join(tmp_folder, "pipe-update-%s.bat" % random_prefix)

        log_file_path = os.path.join(tmp_folder, self.LOG_FILE)
        with open(log_file_path, 'a') as log_file:
            log_file.write('[%s] Starting a new update operation %s' % (datetime.now().strftime("%d/%m/%Y %H:%M:%S"),
                                                                        random_prefix))

        bat_file_content = """@echo off
        for /L %%a in (1,1,{attempts_count}) do (
            tasklist | find /i "{pipe_pid}" >> "{log_file}"
            if errorlevel 1 (
                for /d %%b in ("{src_dir}\\*") do (
                    rd /q /s "%%b" || (
                        echo . >> "{log_file}"
                        echo Failed to delete "%%b" file >> "{log_file}"
                        goto fail
                    )
                )
                echo Source subfolders folder was deleted >> "{log_file}"
                del /q "{src_dir}" >> "{log_file}" || (
                    echo . >> "{log_file}"
                    echo Failed to delete src files >> "{log_file}"
                    goto fail
                )
                xcopy "{tmp_dir}/pipe" "{src_dir}" /y /s /i > nul || (
                    echo . >> "{log_file}"
                    echo Failed to copy files >> "{log_file}"
                    goto fail
                )
                rd /s /q "{tmp_dir}" || (
                    echo . >> "{log_file}"
                    echo Failed to delete tmp directory '{tmp_dir}' >> "{log_file}"
                    goto fail
                )
                goto success
            ) else (
                echo . >> "{log_file}"
                echo Pipeline CLI process '{pipe_pid}' still alive >> "{log_file}"
                timeout /T 2 > nul
            ) 
        )
        goto fail
        
        :success
        echo Pipeline CLI successfully updated
        goto end

        :fail
        echo Fail to update Pipeline CLI
        goto end

        :end
        (goto) 2>nul & del "%~f0
        """.format(attempts_count=self.ATTEMPTS_COUNT,
                   log_file=log_file_path,
                   pipe_pid=os.getpid(),
                   src_dir=path_to_src_dir,
                   tmp_dir=tmp_src_dir)
        with open(path_to_bat, 'w') as bat_file:
            bat_file.write(bat_file_content)

        subprocess.Popen("{}".format(path_to_bat), shell=True)

    def download_new_src(self, path, prefix):
        tmp_folder = self.get_tmp_folder()
        path_to_zip = os.path.join(tmp_folder, self.WINDOWS_SRC_ZIP)
        request = requests.get(path, verify=False)
        open(path_to_zip, 'wb').write(request.content)
        tmp_src_dir = os.path.join(tmp_folder, prefix)
        with zipfile.ZipFile(path_to_zip, 'r') as zip_ref:
            zip_ref.extractall(tmp_src_dir)
        os.remove(path_to_zip)
        return tmp_src_dir

    @staticmethod
    def get_tmp_folder():
        home = os.path.expanduser("~")
        tmp_folder = os.path.join(home, '.pipe', 'tmp')
        if not os.path.exists(tmp_folder):
            os.makedirs(tmp_folder)
        return tmp_folder

    @staticmethod
    def check_write_permissions(path):
        if not os.access(path, os.X_OK & os.W_OK):
            raise RuntimeError("Access denied: the user has no permissions to modify folder '%s'" % path)
