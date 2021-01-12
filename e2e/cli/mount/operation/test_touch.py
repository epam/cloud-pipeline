import os

import pytest

from pyfs import touch, rm
from pyio import write_dirs, read_dirs

operation_config = [
    {
        'name': 'test touch non existing file',
        'target': 'file',
        'before': {},
        'after': {
            'file': ''
        }
    },
    {
        'name': 'test touch non existing file in folder',
        'target': 'folder/file',
        'before': {
            'folder': {}
        },
        'after': {
            'folder': {
                'file': ''
            }
        }
    },
    {
        'name': 'test touch non existing file in subfolder',
        'target': 'folder/subfolder/file',
        'before': {
            'folder': {
                'subfolder': {}
            }
        },
        'after': {
            'folder': {
                'subfolder': {
                    'file': ''
                }
            }
        }
    },
    {
        'name': 'test touch empty file',
        'target': 'file',
        'before': {
            'file': ''
        },
        'after': {
            'file': ''
        }
    },
    {
        'name': 'test touch non empty file',
        'target': 'file',
        'before': {
            'file': 'content'
        },
        'after': {
            'file': 'content'
        }
    }
]


@pytest.fixture(scope='function', autouse=True)
def teardown_function(mount_path):
    yield
    rm(mount_path, under=True, recursive=True, force=True)


@pytest.mark.parametrize('config', operation_config, ids=lambda config: config['name'])
def test_touch(mount_path, config):
    write_dirs(mount_path, config['before'])
    touch(os.path.join(mount_path, config['target']))
    assert read_dirs(mount_path) == config['after']
