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
import shutil
import stat
import sys
import tarfile
from abc import ABCMeta

import click
import requests
import platform
import zipfile
import subprocess
import uuid
from datetime import datetime

from src.config import Config
from src.utilities.version_utils import need_to_update_version

PERMISSION_DENIED_ERROR = "Permission denied: the user has no permissions to modify '%s'"


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

    @staticmethod
    def get_tmp_folder():
        home = os.path.expanduser("~")
        tmp_folder = os.path.join(home, '.pipe', 'tmp')
        if not os.path.exists(tmp_folder):
            os.makedirs(tmp_folder)
        return tmp_folder


class LinuxUpdater(CLIVersionUpdater):

    def __init__(self):
        self.one_file_dist, self.centos_6_dist = self.get_bundle_info()

    BUNDLE_FILE_NAME = "bundle.info"
    ONE_FOLDER = "one-folder"
    ONE_FILE = "one-file"
    CENTOS_6 = "centos:6"

    def get_download_suffix(self):
        prefix = 'pipe-el6' if self.centos_6_dist else 'pipe'
        return prefix + '' if self.one_file_dist else 'tar.gz'

    def update_version(self, path):
        if self.one_file_dist:
            self.one_file_update(path)
        else:
            self.one_folder_update(path)

    def one_folder_update(self, path):
        path_to_exec_folder = sys._MEIPASS
        self.check_write_permissions(path_to_exec_folder)

        tmp_folder = self.get_tmp_folder()
        random_prefix = str(uuid.uuid4()).replace("-", "")
        self.check_write_permissions(tmp_folder)
        path_to_dist_tar = os.path.join(tmp_folder, "pipe-%s.tar.gz" % random_prefix)
        self.download_executable(path_to_dist_tar, path)

        path_to_unpack = os.path.join(tmp_folder, "pipe-%s" % random_prefix)
        with tarfile.open(path_to_dist_tar) as tar:
            tar.extractall(path=path_to_unpack)
        os.remove(path_to_dist_tar)
        self.delete_folder_tree(path_to_exec_folder)
        self.copytree(os.path.join(path_to_unpack, "pipe"), path_to_exec_folder)
        shutil.rmtree(path_to_unpack)

    def one_file_update(self, path):
        path_to_script = os.path.realpath(sys.argv[0])
        self.check_write_permissions(os.path.dirname(path_to_script))
        os.remove(path_to_script)
        self.download_executable(path_to_script, path)
        self.set_x_permission(path_to_script)

    def get_bundle_info(self):
        bundle_file = self.get_bundle_file_path()
        if not os.path.exists(bundle_file):
            return True, False
        dist_type, dist_version = self.get_bundle_file_content(bundle_file)
        is_centos_6_dist = dist_version == self.CENTOS_6
        if dist_type == self.ONE_FILE:
            return True, is_centos_6_dist
        elif dist_type == self.ONE_FOLDER:
            return False, is_centos_6_dist
        else:
            click.echo("Failed to update Cloud Pipeline CLI: update type '%s' not found" % dist_type, err=True)

    @staticmethod
    def download_executable(path_to_file, path):
        request = requests.get(path, verify=False)
        open(path_to_file, 'wb').write(request.content)

    @staticmethod
    def set_x_permission(path_to_script):
        st = os.stat(path_to_script)
        os.chmod(path_to_script, st.st_mode | stat.S_IEXEC)

    @staticmethod
    def check_write_permissions(path):
        if not os.access(path, os.R_OK) or not os.access(path, os.W_OK):
            raise RuntimeError(PERMISSION_DENIED_ERROR % path)

    @staticmethod
    def get_bundle_file_path():
        return os.path.join(sys._MEIPASS, LinuxUpdater.BUNDLE_FILE_NAME)

    @staticmethod
    def get_bundle_file_content(bundle_file_path):
        with open(bundle_file_path, 'r') as bundle_file:
            content = bundle_file.readlines()
        return content[0].strip(), content[1].strip()

    @staticmethod
    def delete_folder_tree(path):
        for root, dirs, files in os.walk(path):
            for f in files:
                os.unlink(os.path.join(root, f))
            for d in dirs:
                shutil.rmtree(os.path.join(root, d))

    @staticmethod
    def copytree(src, dst):
        for item in os.listdir(src):
            source = os.path.join(src, item)
            destination = os.path.join(dst, item)
            if os.path.isdir(source):
                shutil.copytree(source, destination)
            else:
                shutil.copy2(source, destination)


class WindowsUpdater(CLIVersionUpdater):
    WINDOWS_SRC_ZIP = 'pipe.zip'
    ATTEMPTS_COUNT = 10
    LOG_FILE = 'update.log'

    def get_download_suffix(self):
        return self.WINDOWS_SRC_ZIP

    def update_version(self, path):
        random_prefix = str(uuid.uuid4()).replace("-", "")

        tmp_folder = self.get_tmp_folder()
        self.check_write_permissions(tmp_folder, random_prefix)

        path_to_src_dir = os.path.dirname(sys.executable)
        self.check_write_permissions(path_to_src_dir, random_prefix)

        tmp_src_dir = self.download_new_src(path, random_prefix)
        path_to_bat = os.path.join(tmp_folder, "pipe-update-%s.bat" % random_prefix)

        log_file_path = os.path.join(tmp_folder, self.LOG_FILE)
        with open(log_file_path, 'a') as log_file:
            log_file.write('[%s] Starting a new update operation %s\n' % (datetime.now().strftime("%d/%m/%Y %H:%M:%S"),
                                                                          random_prefix))

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
                del /q "{src_dir}" >> "{log_file}" 2>>&1 || (
                    echo Failed to delete src files by path "{log_file}" >> "{log_file}"
                    goto fail
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
                   tmp_dir=tmp_src_dir)
        with open(path_to_bat, 'a') as bat_file:
            bat_file.write(bat_file_content)

        subprocess.Popen("{}".format(path_to_bat))

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
    def check_write_permissions(path, prefix):
        try:
            path_to_tmp_file = os.path.join(path, "tmp-%s" % prefix)
            with open(path_to_tmp_file, 'a') as tmp_file:
                tmp_file.write("")
            os.remove(path_to_tmp_file)
        except OSError:
            raise RuntimeError(PERMISSION_DENIED_ERROR % path)
