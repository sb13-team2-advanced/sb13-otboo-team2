# syntax=docker/dockerfile:1

FROM python:3.12-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    HF_HOME=/opt/huggingface \
    HF_HUB_DISABLE_TELEMETRY=1 \
    OMP_NUM_THREADS=1 \
    MKL_NUM_THREADS=1

WORKDIR /app

COPY deploy/embedding/requirements.txt ./requirements.txt

RUN pip install --no-cache-dir -r requirements.txt \
    && pip check

RUN python -c "from sentence_transformers import SentenceTransformer; model = SentenceTransformer('intfloat/multilingual-e5-small', device='cpu'); assert model.get_sentence_embedding_dimension() == 384"

RUN groupadd --system --gid 10001 app \
    && useradd --uid 10001 --gid app --no-create-home --home-dir /app --shell /usr/sbin/nologin app

COPY --chown=app:app scripts/embedding_server.py ./scripts/embedding_server.py

ENV HF_HUB_OFFLINE=1 \
    TRANSFORMERS_OFFLINE=1

USER app:app

EXPOSE 8001

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8001/openapi.json', timeout=3).close()"]

CMD ["python", "-m", "uvicorn", "scripts.embedding_server:app", "--host", "0.0.0.0", "--port", "8001", "--workers", "1"]
