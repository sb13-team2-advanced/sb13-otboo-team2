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
EMBEDDING_METRICS_URL = None


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


def observe(port):
    with urllib.request.urlopen(f'http://127.0.0.1:{port}/actuator/prometheus', timeout=15) as response:
        lines = response.read().decode().splitlines()
    result = {'time_ms': round(time.time() * 1000), 'pools': {}, 'calls': {}}
    for line in lines:
        if line.startswith('#') or not line.strip():
            continue
        metric, number = line.rsplit(' ', 1)
        if metric.startswith('outbound_http_pool_'):
            tags = dict(re.findall(r'(\w+)="([^"]*)"', metric))
            name = metric.split('{', 1)[0].removeprefix('outbound_http_pool_')
            result['pools'].setdefault(tags['client'], {})[name] = float(number)
        elif metric.startswith(('http_client_requests_seconds_count{', 'http_client_requests_seconds_sum{')):
            tags = dict(re.findall(r'(\w+)="([^"]*)"', metric))
            uri = tags.get('uri', '')
            if uri in ['/embeddings', '/openai/v1/chat/completions']:
                kind = 'count' if metric.startswith('http_client_requests_seconds_count') else 'sum'
                group = result['calls'].setdefault(uri, {'count': 0, 'sum': 0})
                group[kind] += float(number)
        elif metric.startswith('process_cpu_usage'):
            result['cpu'] = float(number)
        elif metric.startswith('hikaricp_connections_pending'):
            result['db_pending'] = float(number)
    if EMBEDDING_METRICS_URL:
        with urllib.request.urlopen(EMBEDDING_METRICS_URL, timeout=5) as response:
            result['embedding_server'] = json.load(response)
    return result


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
    resources_before = observe(port)
    observations = []
    fields = dict(testName=f'{options.label}-{options.mode}-{options.rps}rps-{options.threads}t-{options.runs}runs',
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
        observations.append(observe(port))
        if status != previous:
            print(json.dumps({'test_id': test_id, 'status': status}), flush=True)
            previous = status
        if status in {'FINISHED', 'FINISHED_WITH_WARNING', 'STOP_BY_ERROR', 'CANCELED'}:
            break
        time.sleep(0.5)
    else:
        raise TimeoutError('nGrinder did not finish within 600 seconds')
    after = snapshot(port)
    resources_after = observe(port)
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
    active = [sample for sample in observations if min(row[0] for row in samples) <= sample['time_ms'] <= max(row[1] for row in samples)]
    result['label'] = options.label
    result['pool_metrics'] = {}
    for name, settings in resources_before['pools'].items():
        result['pool_metrics'][name] = {'configured': settings}
        for metric in ['leased', 'pending', 'available']:
            values = [sample['pools'].get(name, {}).get(metric, 0) for sample in active]
            result['pool_metrics'][name][metric + '_max'] = max(values, default=None)
            result['pool_metrics'][name][metric + '_mean'] = round(sum(values) / len(values), 3) if values else None
    result['http_client_mean_ms'] = {}
    for uri, total in resources_after['calls'].items():
        old = resources_before['calls'].get(uri, {'count': 0, 'sum': 0})
        count = total['count'] - old['count']
        result['http_client_mean_ms'][uri] = round((total['sum'] - old['sum']) * 1000 / count, 3) if count else None
    result['cpu_max'] = max((sample.get('cpu', 0) for sample in active), default=None)
    result['db_pending_max'] = max((sample.get('db_pending', 0) for sample in active), default=None)
    result['monitor_samples'] = len(active)
    if 'embedding_server' in resources_after:
        result['embedding_server_mean_ms'] = {}
        for name in ['encode', 'http']:
            current = resources_after['embedding_server']
            old = resources_before['embedding_server']
            count = current[name + '_count'] - old[name + '_count']
            result['embedding_server_mean_ms'][name] = round(
                (current[name + '_seconds'] - old[name + '_seconds']) * 1000 / count, 3) if count else None
            result['embedding_server_mean_ms'][name + '_count'] = count
    (output / f'{test_id}-resources.json').write_text(json.dumps(observations))
    (output / f'{test_id}-summary.json').write_text(json.dumps(result, indent=2))
    print(json.dumps(result), flush=True)
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['probe', 'run', 'mock-suite', 'pool-suite', 'diagnostics'])
    parser.add_argument('--label', default='pool-check')
    parser.add_argument('--embedding-metrics-url')
    parser.add_argument('--port', type=int, default=8081)
    parser.add_argument('--mode', choices=['real', 'mock'], default='mock')
    parser.add_argument('--rps', type=float, default=8.3)
    parser.add_argument('--threads', type=int, default=20)
    parser.add_argument('--runs', type=int, default=13)
    options = parser.parse_args()
    EMBEDDING_METRICS_URL = options.embedding_metrics_url
    if options.action == 'probe':
        probe(options.port)
    elif options.action == 'diagnostics':
        diagnostics(options.port)
    elif options.action in ['mock-suite', 'pool-suite']:
        options.mode = 'mock'
        stages = [(1, 1, 5), (8.3, 20, 13), (16.7, 30, 17), (100, 100, 15 if options.action == 'pool-suite' else 30)]
        for rate, threads, runs in stages:
            options.rps, options.threads, options.runs = rate, threads, runs
            result = run(options)
            if not result['complete'] or result['errors'] or result['ngrinder_status'] != 'FINISHED':
                raise RuntimeError(f"Test {result['test_id']} failed validation; remaining stages were not started")
    else:
        run(options)
