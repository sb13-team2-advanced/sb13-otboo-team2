"""로컬 Groq 대역: 실제 근거를 그대로 반환하고 고정 지연으로 외부 대기를 재현한다."""

import argparse
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Handler(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'

    def do_POST(self):
        if self.path != '/openai/v1/chat/completions':
            self.send_error(404)
            return
        if self.headers.get('Transfer-Encoding', '').lower() == 'chunked':
            chunks = []
            while True:
                size = int(self.rfile.readline().split(b';', 1)[0], 16)
                if size == 0:
                    while self.rfile.readline().strip():
                        pass
                    break
                chunks.append(self.rfile.read(size))
                self.rfile.read(2)
            raw = b''.join(chunks)
        else:
            raw = self.rfile.read(int(self.headers['Content-Length']))
        payload = json.loads(raw)
        context = json.loads(payload['messages'][-1]['content'])
        time.sleep(self.server.delay)
        content = json.dumps({
            'explanation': '상의와 하의가 제공된 배색 근거와 일치하는 테스트용 코디입니다.',
            'documentKey': context['documentKey'],
            'matchedClothesIds': context['matchedClothesIds'],
        }, ensure_ascii=False)
        body = json.dumps({'choices': [{'finish_reason': 'stop', 'message': {'content': content}}]}).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        pass


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--port', type=int, default=8099)
    parser.add_argument('--delay', type=float, default=0.5)
    options = parser.parse_args()
    server = ThreadingHTTPServer(('127.0.0.1', options.port), Handler)
    server.delay = options.delay
    print(f'Groq mock listening on 127.0.0.1:{options.port}, delay={options.delay}s', flush=True)
    server.serve_forever()
