import itertools
import os

from collections import namedtuple

Storage = namedtuple('Storage', ['type', 'region'])

root_path = os.getcwd()
source_path = os.path.join(root_path, 'random.tmp')
host_source_path = os.path.join(root_path, 'random.tmp')
local_path = os.path.join(root_path, 'local-path')
mounted_path = os.path.join(root_path, 'mounted-path')
pipe_path = os.path.join(root_path, 'pipe')
host_pipe_path = os.path.join(root_path, 'pipe')
tests_path = os.path.join(root_path, 'tests')
host_tests_path = os.path.join(root_path, 'tests')
log_path = os.path.join(root_path, 'log.txt')

default_distributions = ['lifescience/cloud-pipeline:tools-base-ubuntu-18.04-0.16',
                         'lifescience/cloud-pipeline:tools-base-centos-7-0.16']
default_storages = [Storage(type='S3', region='1')]
default_sizes = None
default_folder = None
containers = {}


def pytest_addoption(parser):
    parser.addoption('--distributions', help='Linux distributions to perform tests for.')
    parser.addoption('--storages', help='Testing storage types to perform tests for.')
    parser.addoption('--sizes', help='File sizes to perform tests for.')
    parser.addoption('--small', help='Performs test scenarios using small sized files.', action='store_true')
    parser.addoption('--big', help='Performs test scenarios using big sized files.', action='store_true')
    parser.addoption('--folder', help='Cloud Pipeline folder id to create testing storages in.')


def pytest_generate_tests(metafunc):
    distributions = _get_distributions(metafunc.config)
    storages = _get_storages(metafunc.config) or default_storages
    distribution_storage_combinations = list(itertools.product(distributions, storages))
    folder = metafunc.config.option.sizes or default_folder
    pytest_arguments = _get_pytest_arguments(metafunc.config)
    parameters = [[dsc[0], dsc[1], folder, pytest_arguments] for dsc in distribution_storage_combinations]
    ids = [dsc[1].type + ' on ' + dsc[0] for dsc in distribution_storage_combinations]
    metafunc.parametrize('distribution, storage, folder, pytest_arguments', parameters, ids=ids)


def _get_pytest_arguments(config):
    if config.option.small:
        return '--small'
    if config.option.big:
        return '--big'
    sizes = config.option.sizes or default_sizes
    return '--sizes %s' % sizes if sizes else ''


def _get_storages(config):
    storages_string = config.option.storages
    if storages_string:
        import json
        storages_json = json.loads(storages_string)
        storages = [Storage(**storage) for storage in storages_json]
    else:
        storages = default_storages
    return storages


def _get_distributions(config):
    distributions_string = config.option.distributions
    if distributions_string:
        distributions = [raw_distribution.strip()
                         for raw_distribution in distributions_string.split(',')
                         if raw_distribution.strip()]
    else:
        distributions = default_distributions
    return sorted(distributions)
