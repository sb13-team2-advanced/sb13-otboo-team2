"""로컬 nGrinder의 유한 요청 실행과 서버 지표·완료 로그를 함께 수집한다."""

import argparse
import base64
import io
import json
import math
from pathlib import Path
import re
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent
CONTROLLER = 'http://127.0.0.1:8300'
AUTH = 'Basic ' + base64.b64encode(b'admin:admin').decode()


def request(path, fields=None):
    data = urllib.parse.urlencode(fields).encode() if fields is not None else None
    req = urllib.request.Request(CONTROLLER + path, data=data, headers={'Authorization': AUTH})
    with urllib.request.urlopen(req, timeout=30) as response:
        return response.read()


def snapshot(port):
    with urllib.request.urlopen(f'http://127.0.0.1:{port}/actuator/prometheus', timeout=15) as response:
        text = response.read().decode()
    values = {}
    for line in text.splitlines():
        if line.startswith('recommendation_explanations_total{'):
            tags = dict(re.findall(r'(\w+)="([^"]*)"', line))
            values[tags['outcome'] + ':' + tags['reason']] = float(line.rsplit(' ', 1)[1])
    return values


def probe(port):
    settings = dict(line.split('=', 1) for line in (ROOT / 'target.properties').read_text().splitlines() if '=' in line)
    url = f'http://127.0.0.1:{port}/api/recommendations?weatherId=' + settings['weatherId'].strip()
    req = urllib.request.Request(url, headers={'Authorization': 'Bearer ' + settings['accessToken'].strip()})
    start = time.monotonic()
    with urllib.request.urlopen(req, timeout=30) as response:
        body = json.load(response)
        print(json.dumps({'status': response.status, 'latency_ms': round((time.monotonic() - start) * 1000),
                          'clothes': len(body.get('clothes', [])), 'sources': len(body.get('sources', []))}), flush=True)
    print(json.dumps({'explanations': snapshot(port)}), flush=True)


def diagnostics(port):
    result = {}
    for name, tags in [('http.client.requests', ['uri:/embeddings']),
                       ('http.client.requests', ['uri:/chat/completions']),
                       ('http.client.requests', ['uri:/openai/v1/chat/completions']),
                       ('process.cpu.usage', []), ('hikaricp.connections.pending', [])]:
        query = urllib.parse.urlencode([('tag', tag) for tag in tags])
        url = f'http://127.0.0.1:{port}/actuator/metrics/{name}?{query}'
        try:
            with urllib.request.urlopen(url, timeout=15) as response:
                body = json.load(response)
                result[name + str(tags)] = body['measurements']
        except urllib.error.HTTPError as exception:
            result[name + str(tags)] = {'status': exception.code}
    print(json.dumps(result), flush=True)


def run(options):
    port = 8082 if options.mode == 'mock' else 8081
    before = snapshot(port)
    fields = dict(testName=f'{options.mode}-{options.rps}rps-{options.threads}t-{options.runs}runs',
                  scriptName='RecommendationLoadTest.groovy', scm='svn', scriptRevision='-1',
                  status='READY', threshold='R', runCount=options.runs, duration=600000,
                  agentCount=1, vuserPerAgent=options.threads, processes=1, threads=options.threads,
                  region='NONE', samplingInterval=1, ignoreSampleCount=0,
                  param=f'{options.mode}_{round(options.rps * 10)}')
    model = json.loads(request('/perftest/api', fields))
    test_id = model['id']
    print(json.dumps({'test_id': test_id, 'expected_requests': options.threads * options.runs}), flush=True)
    previous = None
    deadline = time.monotonic() + 600
    while time.monotonic() < deadline:
        model = json.loads(request(f'/perftest/api/{test_id}'))
        status = model['status']['name']
        if status != previous:
            print(json.dumps({'test_id': test_id, 'status': status}), flush=True)
            previous = status
        if status in {'FINISHED', 'FINISHED_WITH_WARNING', 'STOP_BY_ERROR', 'CANCELED'}:
            break
        time.sleep(2)
    else:
        raise TimeoutError('nGrinder did not finish within 600 seconds')
    after = snapshot(port)
    delta = {key: after.get(key, 0) - before.get(key, 0) for key in after.keys() | before.keys()}
    filenames = json.loads(request(f'/perftest/api/{test_id}/logs'))
    samples = []
    output = ROOT / 'results'
    output.mkdir(exist_ok=True)
    for filename in filenames:
        archive = request(f'/perftest/{test_id}/download_log/{urllib.parse.quote(filename)}')
        (output / f'{test_id}-{filename}').write_bytes(archive)
        with zipfile.ZipFile(io.BytesIO(archive)) as logs:
            for name in logs.namelist():
                text = logs.read(name).decode(errors='replace')
                samples.extend(tuple(map(int, row[:4])) + (row[4] == 'true',) for row in re.findall(
                    r'PERF startMs=(\d+) endMs=(\d+) status=(\d+) latencyMicros=(\d+) valid=(true|false)', text))
    if not samples:
        raise RuntimeError(f'No request samples; inspect results/{test_id}-*.zip')
    latencies = sorted(row[3] / 1000 for row in samples)
    seconds = (max(row[1] for row in samples) - min(row[0] for row in samples)) / 1000
    arrival_seconds = (max(row[0] for row in samples) - min(row[0] for row in samples)) / 1000 + 1 / options.rps
    success = sum(row[2] == 200 and row[4] for row in samples)
    result = {'test_id': test_id, 'mode': options.mode, 'target_rps': options.rps, 'samples': len(samples),
              'expected': options.threads * options.runs, 'complete': len(samples) == options.threads * options.runs,
              'duration_seconds': round(seconds, 3), 'arrival_rps': round(len(samples) / arrival_seconds, 3),
              'completed_rps': round(len(samples) / seconds, 3), 'success_tps': round(success / seconds, 3),
              'errors': len(samples) - success, 'mean_ms': round(sum(latencies) / len(latencies), 2),
              'p50_ms': latencies[math.ceil(len(latencies) * .50) - 1],
              'p95_ms': latencies[math.ceil(len(latencies) * .95) - 1],
              'p99_ms': latencies[math.ceil(len(latencies) * .99) - 1], 'max_ms': latencies[-1],
              'explanations': delta, 'ngrinder_status': model['status']['name']}
    (output / f'{test_id}-summary.json').write_text(json.dumps(result, indent=2))
    print(json.dumps(result), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['probe', 'run', 'mock-suite', 'diagnostics'])
    parser.add_argument('--port', type=int, default=8081)
    parser.add_argument('--mode', choices=['real', 'mock'], default='mock')
    parser.add_argument('--rps', type=float, default=8.3)
    parser.add_argument('--threads', type=int, default=20)
    parser.add_argument('--runs', type=int, default=13)
    options = parser.parse_args()
    if options.action == 'probe':
        probe(options.port)
    elif options.action == 'diagnostics':
        diagnostics(options.port)
    elif options.action == 'mock-suite':
        options.mode = 'mock'
        for rate, threads, runs in [(1, 1, 5), (8.3, 20, 13), (16.7, 30, 17), (100, 100, 30)]:
            options.rps, options.threads, options.runs = rate, threads, runs
            run(options)
    else:
        run(options)
