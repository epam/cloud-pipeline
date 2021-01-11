import os

from pyfs import touch, rm, mkdir, truncate


def test_touch_non_existing_file(mount_path):
    file_path = os.path.join(mount_path, 'file')
    rm(file_path, force=True)
    touch(file_path)
    assert os.path.isfile(file_path)
    assert os.path.getsize(file_path) == 0


def test_touch_non_existing_file_in_folder(mount_path):
    folder_path = os.path.join(mount_path, 'folder')
    file_path = os.path.join(folder_path, 'file')
    rm(folder_path, recursive=True, force=True)
    mkdir(folder_path)
    touch(file_path)
    assert os.path.isfile(file_path)
    assert os.path.getsize(file_path) == 0


def test_touch_non_existing_file_in_subfolder(mount_path):
    folder_path = os.path.join(mount_path, 'folder')
    subfolder_path = os.path.join(folder_path, 'subfolder')
    file_path = os.path.join(folder_path, 'file')
    rm(folder_path, recursive=True, force=True)
    mkdir(subfolder_path, recursive=True)
    touch(file_path)
    assert os.path.isfile(file_path)
    assert os.path.getsize(file_path) == 0


def test_touch_empty_file(mount_path):
    file_path = os.path.join(mount_path, 'file')
    truncate(file_path, size=0)
    touch(file_path)
    assert os.path.isfile(file_path)
    assert os.path.getsize(file_path) == 0


def test_touch_non_empty_file(mount_path):
    file_path = os.path.join(mount_path, 'file')
    truncate(file_path, size=1)
    touch(file_path)
    assert os.path.isfile(file_path)
    assert os.path.getsize(file_path) == 1
