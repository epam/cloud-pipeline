import inspect
import logging

import pytest
from py.xml import html


@pytest.hookimpl(hookwrapper=True)
def pytest_runtest_makereport(item, call):
    outcome = yield
    report = outcome.get_result()
    function_docs = (inspect.getdoc(item.function) or '').strip().splitlines() or ['']
    function_spec = inspect.getargspec(item.function)
    function_defaults = dict(zip(function_spec.args[-len(function_spec.defaults):], function_spec.defaults))

    doc_test_case = function_docs[0].strip()
    explicit_argument_test_case = item.funcargs.get('test_case', '')
    default_argument_test_case = function_defaults.get('test_case', '')
    instance_test_case = getattr(item.instance, 'test_case', '')
    report.test_case = doc_test_case \
                       or explicit_argument_test_case \
                       or default_argument_test_case \
                       or instance_test_case \
                       or ''
    if report.failed:
        logging.error('%s: %s FAILED:\n %s', report.test_case, report.nodeid, report.longrepr)
    report.longrepr = None


def pytest_html_results_table_header(cells):
    cells.insert(0, html.th('Test Case'))


def pytest_html_results_table_row(report, cells):
    cells.insert(0, html.td(str(report.test_case)))
