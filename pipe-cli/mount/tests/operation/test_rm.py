import os

from pyfs import mkdir, rm, touch


def test_rm_file(mount_path):
    file_path = os.path.join(mount_path, 'file')
    touch(file_path)
    rm(file_path)
    assert not os.path.isfile(file_path)


def test_rm_file_in_folder(mount_path):
    folder_path = os.path.join(mount_path, 'folder')
    file_path = os.path.join(folder_path, 'file')
    mkdir(folder_path, recursive=True)
    touch(file_path)
    rm(file_path)
    assert not os.path.isfile(file_path)
    rm(folder_path, recursive=True)


def test_rm_file_in_subfolder(mount_path):
    folder_path = os.path.join(mount_path, 'folder')
    subfolder_path = os.path.join(folder_path, 'subfolder')
    file_path = os.path.join(subfolder_path, 'file')
    mkdir(subfolder_path, recursive=True)
    touch(file_path)
    rm(file_path)
    assert not os.path.isfile(file_path)
    rm(folder_path, recursive=True)


def test_rm_file_with_space_in_name(mount_path):
    file_path = os.path.join(mount_path, 'file name')
    touch(file_path)
    rm(file_path)
    assert not os.path.isfile(file_path)


def test_rm_file_with_uppercase_in_name(mount_path):
    file_path = os.path.join(mount_path, 'FILE')
    touch(file_path)
    rm(file_path)
    assert not os.path.isfile(file_path)


def test_rm_folder(mount_path):
    folder_path = os.path.join(mount_path, 'folder')
    mkdir(folder_path)
    rm(folder_path, recursive=True)
    assert not os.path.isdir(folder_path)


def test_rm_subfolder(mount_path):
    folder_path = os.path.join(mount_path, 'folder')
    subfolder_path = os.path.join(folder_path, 'subfolder')
    mkdir(subfolder_path, recursive=True)
    rm(subfolder_path, recursive=True)
    assert not os.path.isdir(subfolder_path)
    assert os.path.isdir(folder_path)


def test_rm_folder_with_subfolder(mount_path):
    folder_path = os.path.join(mount_path, 'folder')
    subfolder_path = os.path.join(folder_path, 'subfolder')
    mkdir(subfolder_path, recursive=True)
    rm(folder_path, recursive=True)
    assert not os.path.isdir(subfolder_path)
    assert not os.path.isdir(folder_path)


def test_rm_folder_with_files(mount_path):
    folder_path = os.path.join(mount_path, 'folder')
    file_path = os.path.join(folder_path, 'file')
    mkdir(folder_path)
    touch(file_path)
    rm(folder_path, recursive=True)
    assert not os.path.isfile(file_path)
    assert not os.path.isdir(folder_path)


def test_rm_subfolder_with_files(mount_path):
    folder_path = os.path.join(mount_path, 'folder')
    subfolder_path = os.path.join(folder_path, 'subfolder')
    file_path = os.path.join(subfolder_path, 'file')
    mkdir(subfolder_path, recursive=True)
    touch(file_path)
    rm(subfolder_path, recursive=True)
    assert not os.path.isfile(file_path)
    assert not os.path.isdir(subfolder_path)
    assert os.path.isdir(folder_path)


def test_rm_folder_with_subfolder_with_files(mount_path):
    folder_path = os.path.join(mount_path, 'folder')
    subfolder_path = os.path.join(folder_path, 'subfolder')
    file_path = os.path.join(subfolder_path, 'file')
    mkdir(subfolder_path, recursive=True)
    touch(file_path)
    rm(folder_path, recursive=True)
    assert not os.path.isfile(file_path)
    assert not os.path.isdir(subfolder_path)
    assert not os.path.isdir(folder_path)
