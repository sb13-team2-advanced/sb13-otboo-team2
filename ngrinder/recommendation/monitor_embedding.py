"""로컬 임베딩 컨테이너의 Docker 자원 표본을 JSONL로 저장한다."""

import argparse
import json
from pathlib import Path
import re
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument('--container', default='otboo-embedding-measurement')
parser.add_argument('--output', required=True)
options = parser.parse_args()
output = Path(options.output)
output.parent.mkdir(parents=True, exist_ok=True)
process = subprocess.Popen(['docker', 'stats', '--format', '{{json .}}', options.container],
                           stdout=subprocess.PIPE, text=True)
try:
    with output.open('w') as stream:
        for line in process.stdout:
            line = re.sub(r'\x1b\[[0-9;]*[A-Za-z]', '', line).strip()
            if not line:
                continue
            sample = json.loads(line)
            sample['time_ms'] = round(time.time() * 1000)
            stream.write(json.dumps(sample) + '\n')
            stream.flush()
finally:
    process.terminate()
    process.wait()
