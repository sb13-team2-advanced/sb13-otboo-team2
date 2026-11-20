"""검색 문장을 벡터로 변환해 Spring에 반환하는 로컬 HTTP 서버."""

from fastapi import FastAPI
from pydantic import BaseModel, ConfigDict, Field
from sentence_transformers import SentenceTransformer

MODEL_NAME = "intfloat/multilingual-e5-small"

app = FastAPI(title="OOTD Embedding API")
model = SentenceTransformer(MODEL_NAME)


class EmbeddingRequest(BaseModel):
    """공백을 정리하고 비어 있지 않은 검색 문장을 받는다."""

    model_config = ConfigDict(str_strip_whitespace=True)
    text: str = Field(min_length=1)


class EmbeddingResponse(BaseModel):
    """사용한 모델명과 벡터 차원, 실제 벡터를 반환한다."""

    model: str
    dimensions: int
    embedding: list[float]


@app.post("/embeddings", response_model=EmbeddingResponse)
def create_embedding(request: EmbeddingRequest):
    """검색 문장을 query 형식으로 변환하고 정규화된 벡터를 생성한다."""
    vector = model.encode(
        f"query: {request.text}",
        normalize_embeddings=True,
    )

    return EmbeddingResponse(
        model=MODEL_NAME,
        dimensions=len(vector),
        embedding=vector.tolist(),
    )