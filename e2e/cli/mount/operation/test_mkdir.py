import os

import pytest

from pyfs import mkdir, rm, touch

operation_config = [
    {
        'name': 'mkdir folder',
        'target': 'folder',
        'before': {},
        'after': {
            'folder': {}
        }
    },
    {
        'name': 'mkdir subfolder',
        'target': 'folder/subfolder',
        'before': {
            'folder': {}
        },
        'after': {
            'folder': {
                'subfolder': {}
            }
        }
    },
    {
        'name': 'mkdir subfolder with parent',
        'target': 'folder/subfolder',
        'before': {},
        'after': {
            'folder': {
                'subfolder': {}
            }
        }
    },
    {
        'name': 'mkdir folder with space in name',
        'target': 'folder name',
        'before': {},
        'after': {
            'folder name': {}
        }
    },
    {
        'name': 'mkdir folder with uppercase letters in name',
        'target': 'FOLDER',
        'before': {},
        'after': {
            'FOLDER': {}
        }
    }
]


@pytest.fixture(scope='function', autouse=True)
def teardown_function(mount_path):
    yield
    rm(mount_path, under=True, recursive=True, force=True)


@pytest.mark.parametrize('config', operation_config, ids=lambda config: config['name'])
def test_mkdir(mount_path, config):
    create_dirs(mount_path, config['before'])
    makedir(mount_path, config['target'])
    assert collect_dirs(mount_path) == config['after']


def create_dirs(path, dirs):
    for k, v in dirs.items():
        curr_path = os.path.join(path, k)
        if isinstance(v, str):
            touch(curr_path)
        else:
            mkdir(curr_path)
            create_dirs(curr_path, v)


def makedir(mount_path, target):
    target_path = os.path.join(mount_path, target)
    mkdir(target_path, recursive=not os.path.exists(os.path.dirname(target_path)))


def collect_dirs(path):
    dirs = {}
    for item in os.listdir(path):
        item_path = os.path.join(path, item)
        if os.path.isfile(item_path):
            dirs[item] = ''
        if os.path.isdir(item_path):
            dirs[item] = collect_dirs(item_path)
    return dirs
