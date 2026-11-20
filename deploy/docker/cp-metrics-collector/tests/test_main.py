# Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import threading
import urllib.request

import pytest

from metrics_collector import config, main
from metrics_collector.collector import BatchResult


@pytest.mark.parametrize('now, batch, at', [(1000.0, 1020, 1025), (1019.9, 1020, 1025), (1020.0, 1040, 1045)])
def test_next_batch_is_the_window_end_plus_the_offset(now, batch, at):
    assert main.next_batch(now, 20, 5) == (batch, at)


def test_health():
    now = [1000.0]
    health = main.Health(30, clock=lambda: now[0])
    now[0] = 1180
    assert health.status() == (True, 'ok')
    now[0] = 1181
    healthy, message = health.status()
    assert not healthy and '181' in message
    health.success()
    assert health.status()[0]
    health = main.Health(120, clock=lambda: now[0])
    now[0] += 360
    assert health.status()[0]


class Clock:
    """A clock that `wait` advances, so the loop runs at once."""

    def __init__(self, start):
        self.now = start

    def __call__(self):
        return self.now


class Stop:
    def __init__(self, clock, batches):
        self.clock = clock
        self.left = batches

    def is_set(self):
        return self.left <= 0

    def wait(self, seconds):
        self.clock.now += seconds
        return self.left <= 0


class RecordingCollector:
    def __init__(self, stop, fail_on=()):
        self.stop = stop
        self.fail_on = fail_on
        self.batches = []

    def collect(self, epoch):
        self.batches.append((epoch, self.stop.clock.now))
        self.stop.left -= 1
        self.stop.clock.now += 3
        if epoch in self.fail_on:
            raise RuntimeError('boom')
        return BatchResult(nodes=1, scraped=1, documents=4, rejected=0)


def _config():
    return config.from_env({'KUBERNETES_SERVICE_HOST': 'h', 'CP_METRICS_COLLECTOR_RESOLUTION': '30s'})


def test_run_collects_each_window_after_its_end():
    clock = Clock(1000.0)
    stop = Stop(clock, 3)
    collector = RecordingCollector(stop)
    health = main.Health(30, clock=clock)
    main.run(_config(), collector, health, stop, clock=clock)
    assert collector.batches == [(1020, 1025), (1050, 1055), (1080, 1085)]


def test_a_failed_batch_does_not_stop_the_loop_or_count_as_healthy():
    clock = Clock(1000.0)
    stop = Stop(clock, 2)
    collector = RecordingCollector(stop, fail_on=(1020,))
    health = main.Health(30, clock=clock)
    main.run(_config(), collector, health, stop, clock=clock)
    assert [epoch for epoch, _ in collector.batches] == [1020, 1050]
    assert health._last_success == 1058


def test_healthz_endpoint():
    health = main.Health(30)
    server = main.ThreadingHTTPServer(('127.0.0.1', 0), main._handler(health))
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        url = 'http://127.0.0.1:%d' % server.server_address[1]
        with urllib.request.urlopen(url + '/healthz') as response:
            assert response.status == 200 and response.read() == b'ok\n'
        with pytest.raises(urllib.error.HTTPError) as error:
            urllib.request.urlopen(url + '/other')
        assert error.value.code == 404
    finally:
        server.shutdown()


def test_main_reports_a_configuration_error(capsys):
    assert main.main({}) == 2
    assert 'Configuration error' in capsys.readouterr().err
