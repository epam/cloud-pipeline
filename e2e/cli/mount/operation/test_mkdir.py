import os

from pytest import fail

from pyfs import mkdir


def test_mkdir_folder(mount_path):
    folder_path = os.path.join(mount_path, 'folder')
    mkdir(folder_path)
    assert os.path.isdir(folder_path)


def test_mkdir_subfolder(mount_path):
    folder_path = os.path.join(mount_path, 'folder')
    subfolder_path = os.path.join(folder_path, 'subfolder')
    mkdir(subfolder_path, recursive=True)
    assert os.path.isdir(subfolder_path)


def test_mkdir_subfolder_without_parent(mount_path):
    folder_path = os.path.join(mount_path, 'folder')
    subfolder_path = os.path.join(folder_path, 'subfolder')
    mkdir(subfolder_path, recursive=True)
    assert os.path.isdir(subfolder_path)


def test_mkdir_existing_folder(mount_path):
    folder_path = os.path.join(mount_path, 'folder')
    try:
        mkdir(folder_path)
        fail('Existing folder cannot be created.')
    except RuntimeError as e:
        assert 'exists' in str(e)


def test_mkdir_folder_with_space_in_name(mount_path):
    folder_path = os.path.join(mount_path, 'folder name')
    mkdir(folder_path)
    assert os.path.isdir(folder_path)


def test_mkdir_folder_with_uppercase_in_name(mount_path):
    folder_path = os.path.join(mount_path, 'FOLDER')
    mkdir(folder_path)
    assert os.path.isdir(folder_path)
