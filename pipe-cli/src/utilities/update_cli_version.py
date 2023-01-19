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

import click
import requests
import platform
import zipfile
import uuid
from datetime import datetime

from src.config import Config
from src.utilities.version_utils import need_to_update_version

PERMISSION_DENIED_ERROR = "Permission denied: the user has no permissions to modify '%s'"
WRAPPER_SUPPORT_ONLY_ERROR = "Update operation is not available."


class UpdateCLIVersionManager(object):

    def __init__(self):
        pass

    def update(self, path=None):
        if not need_to_update_version():
            click.echo("The Cloud Pipeline CLI version is up-to-date")
            return

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
        path_to_script = os.path.realpath(sys.executable)
        self.check_write_permissions(os.path.dirname(path_to_script))
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

    @staticmethod
    def check_write_permissions(path):
        if not os.access(path, os.R_OK) or not os.access(path, os.W_OK):
            raise RuntimeError(PERMISSION_DENIED_ERROR % path)


class WindowsUpdater(CLIVersionUpdater):
    WINDOWS_SRC_ZIP = 'pipe.zip'
    ATTEMPTS_COUNT = 10
    LOG_FILE = 'update.log'
    WRAPPER_BAT = 'pipe.bat' # a static pipe executable, this file shall not be updated
    WRAPPER_UPDATE_ENV = 'CP_CLI_UPDATE_WRAPPER'
    UPDATE_SCRIPT = 'pipe-cli-update.bat'
    TMP_UNZIP_FOLDER = 'pipe'

    def get_download_suffix(self):
        return self.WINDOWS_SRC_ZIP

    def update_version(self, path):
        if os.environ.get(self.WRAPPER_UPDATE_ENV) != "true":
            raise RuntimeError(WRAPPER_SUPPORT_ONLY_ERROR)

        tmp_folder = self.get_tmp_folder()
        self.check_write_permissions(tmp_folder)

        path_to_src_dir = os.path.dirname(sys.executable)
        self.check_write_permissions(path_to_src_dir)

        tmp_src_dir = self.download_new_src(path, self.TMP_UNZIP_FOLDER)
        pipe_bat = os.path.join(tmp_src_dir, self.WRAPPER_BAT)
        if os.path.isfile(pipe_bat):
            os.remove(pipe_bat)

        path_to_update_bat = os.path.join(tmp_folder, self.UPDATE_SCRIPT)

        log_file_path = os.path.join(tmp_folder, self.LOG_FILE)
        with open(log_file_path, 'a') as log_file:
            log_file.write('[%s] Starting a new update operation\n' % (datetime.now().strftime("%d/%m/%Y %H:%M:%S")))

        bat_file_content = """@echo off
        for /L %%a in (1,1,{attempts_count}) do (
            tasklist | find /i " {pipe_pid} " >> "{log_file}" 2>>&1
            if errorlevel 1 (
                for /d %%b in ("{src_dir}\\*") do (
                    rd /q /s "%%b" 2>> "{log_file}" || (
                        echo Failed to delete "%%b" >> "{log_file}"
                        goto fail
                    )
                )
                echo The source subfolders were deleted >> "{log_file}"
                for %%a in ("{src_dir}\\*") do (
	                if /i not "%%~nxa" == "{pipe_bat}" (
	                    del /q "%%a" >> "{log_file}" 2>>&1 || (
                            echo Failed to delete src file "%%a" >> "{log_file}"
                            goto fail
                        )
	                )
	            )
                echo The source files were deleted >> "{log_file}"
                xcopy "{tmp_dir}\\pipe" "{src_dir}" /y /s /i > nul 2>> "{log_file}" || (
                    echo Failed to copy files from "{tmp_dir}/pipe" to "{src_dir}" >> "{log_file}"
                    goto fail
                )
                echo Files successfully copied from "{tmp_dir}/pipe" to "{src_dir}" >> "{log_file}"
                rd /s /q "{tmp_dir}" || (
                    echo Failed to delete tmp directory '{tmp_dir}' >> "{log_file}"
                    goto fail
                )
                goto success
            ) else (
                echo Pipeline CLI process '{pipe_pid}' still alive >> "{log_file}"
                timeout /T 2 > nul
            ) 
        )
        goto fail
        
        :success
        echo Success! >> "{log_file}"
        goto end

        :fail
        echo Failure! >> "{log_file}"
        echo Failed to update Pipeline CLI. For more details see logs: "{log_file}"
        goto end

        :end
        goto 2>nul & del "%~f0
        """.format(attempts_count=self.ATTEMPTS_COUNT,
                   log_file=log_file_path,
                   pipe_pid=os.getpid(),
                   src_dir=path_to_src_dir,
                   tmp_dir=tmp_src_dir,
                   pipe_bat=self.WRAPPER_BAT)
        with open(path_to_update_bat, 'a') as bat_file:
            bat_file.write(bat_file_content)

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
        random_prefix = str(uuid.uuid4()).replace("-", "")
        try:
            path_to_tmp_file = os.path.join(path, "tmp-%s" % random_prefix)
            with open(path_to_tmp_file, 'a') as tmp_file:
                tmp_file.write("")
            os.remove(path_to_tmp_file)
        except OSError:
            raise RuntimeError(PERMISSION_DENIED_ERROR % path)
