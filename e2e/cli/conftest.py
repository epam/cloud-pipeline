import logging

import pytest
from py.xml import html


@pytest.hookimpl(hookwrapper=True)
def pytest_runtest_makereport(item, call):
    outcome = yield
    report = outcome.get_result()
    funtion_docs = (item.function.__doc__ or '').strip().splitlines()
    docs_test_case = funtion_docs[0].strip() if funtion_docs else ''
    arguments_test_case = item.funcargs.get('test_case', '')
    instnace_test_case = getattr(item.instance, 'test_case', '')
    report.test_case = docs_test_case \
                       or arguments_test_case \
                       or instnace_test_case \
                       or ''
    if report.failed:
        logging.error('%s: %s FAILED:\n %s', report.test_case, report.nodeid, report.longrepr)
    report.longrepr = None


def pytest_html_results_table_header(cells):
    cells.insert(0, html.th('Test Case'))


def pytest_html_results_table_row(report, cells):
    cells.insert(0, html.td(str(report.test_case)))
