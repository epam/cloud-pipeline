import os

import pytest

from pyfs import mkdir, rm, touch

operation_config = [
    {
        'name': 'rm file',
        'target': 'file',
        'before': {
            'file': ''
        },
        'after': {}
    },
    {
        'name': 'rm file in folder',
        'target': 'folder/file',
        'before': {
            'folder': {
                'file': ''
            }
        },
        'after': {
            'folder': {}
        }
    },
    {
        'name': 'rm file in subfolder',
        'target': 'folder/subfolder/file',
        'before': {
            'folder': {
                'subfolder': {
                    'file': ''
                }
            }
        },
        'after': {
            'folder': {
                'subfolder': {}
            }
        }
    },
    {
        'name': 'rm file with space in name',
        'target': 'file name',
        'before': {
            'file name': ''
        },
        'after': {}
    },
    {
        'name': 'rm file with uppercase letters in name',
        'target': 'FILE',
        'before': {
            'FILE': ''
        },
        'after': {}
    },
    {
        'name': 'rm folder',
        'target': 'folder',
        'before': {
            'folder': {}
        },
        'after': {}
    },
    {
        'name': 'rm subfolder',
        'target': 'folder/subfolder',
        'before': {
            'folder': {
                'subfolder': {}
            }
        },
        'after': {
            'folder': {}
        }
    },
    {
        'name': 'rm folder with subfolder',
        'target': 'folder',
        'before': {
            'folder': {
                'subfolder': {}
            }
        },
        'after': {}
    },
    {
        'name': 'rm folder with file',
        'target': 'folder',
        'before': {
            'folder': {
                'file': ''
            }
        },
        'after': {}
    },
    {
        'name': 'rm subfolder with file',
        'target': 'folder/subfolder',
        'before': {
            'folder': {
                'subfolder': {
                    'file': ''
                }
            }
        },
        'after': {
            'folder': {}
        }
    },
    {
        'name': 'rm folder with subfolder with file',
        'target': 'folder',
        'before': {
            'folder': {
                'subfolder': {
                    'file': ''
                }
            }
        },
        'after': {}
    }
]


@pytest.fixture(scope='function', autouse=True)
def teardown_function(session_mount_path):
    yield
    rm(session_mount_path, under=True, recursive=True, force=True)


@pytest.mark.parametrize('config', operation_config, ids=lambda config: config['name'])
def test_rm(mount_path, config):
    create_dirs(mount_path, config['before'])
    remove(mount_path, config['target'])
    assert collect_dirs(mount_path) == config['after']


def create_dirs(path, dirs):
    for k, v in dirs.items():
        curr_path = os.path.join(path, k)
        if isinstance(v, str):
            touch(curr_path)
        else:
            mkdir(curr_path)
            create_dirs(curr_path, v)


def remove(mount_path, target):
    target_path = os.path.join(mount_path, target)
    rm(target_path, recursive=os.path.isdir(target_path))


def collect_dirs(path):
    dirs = {}
    for item in os.listdir(path):
        item_path = os.path.join(path, item)
        if os.path.isfile(item_path):
            dirs[item] = ''
        if os.path.isdir(item_path):
            dirs[item] = collect_dirs(item_path)
    return dirs
