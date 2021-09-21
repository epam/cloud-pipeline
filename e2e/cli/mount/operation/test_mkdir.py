import os

import pytest

from pyfs import mkdir, rm
from pyio import write_dirs, read_dirs

operation_config = [
    {
        'case': 'TC-PIPE-FUSE-27',
        'name': 'mkdir folder',
        'target': 'folder',
        'before': {},
        'after': {
            'folder': {}
        }
    },
    {
        'case': 'TC-PIPE-FUSE-28',
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
        'case': 'TC-PIPE-FUSE-29',
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
        'case': 'TC-PIPE-FUSE-30',
        'name': 'mkdir folder with space in name',
        'target': 'folder name',
        'before': {},
        'after': {
            'folder name': {}
        }
    },
    {
        'case': 'TC-PIPE-FUSE-31',
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


@pytest.mark.parametrize('config,test_case', zip(operation_config, [config['case'] for config in operation_config]),
                         ids=[config['name'] for config in operation_config])
def test_mkdir(mount_path, config, test_case):
    write_dirs(mount_path, config['before'])
    target_path = os.path.join(mount_path, config['target'])
    mkdir(target_path, recursive=not os.path.exists(os.path.dirname(target_path)))
    assert read_dirs(mount_path) == config['after']
