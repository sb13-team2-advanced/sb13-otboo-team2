"""기존 임베딩 앱에 측정용 시간 카운터를 추가하는 로컬 실행 진입점."""

from threading import Lock
from time import perf_counter

from scripts import embedding_server

app = embedding_server.app
lock = Lock()
totals = {"encode_count": 0, "encode_seconds": 0.0, "http_count": 0, "http_seconds": 0.0}


def record(name, seconds):
    with lock:
        totals[name + "_count"] += 1
        totals[name + "_seconds"] += seconds


class ObservedModel:
    """モデルと入力を変更せず、encodeの壁時計時間だけ記録する。"""

    def __init__(self, delegate):
        self.delegate = delegate

    def encode(self, *args, **kwargs):
        started = perf_counter()
        try:
            return self.delegate.encode(*args, **kwargs)
        finally:
            record("encode", perf_counter() - started)


embedding_server.model = ObservedModel(embedding_server.model)


@app.middleware("http")
async def observe_request(request, call_next):
    started = perf_counter()
    try:
        return await call_next(request)
    finally:
        if request.method == "POST" and request.url.path == "/embeddings":
            record("http", perf_counter() - started)


@app.get("/_loadtest/metrics")
def metrics():
    """推論とHTTP処理の累積値を返す。接続前の待機時間は含まない。"""
    with lock:
        return dict(totals)
