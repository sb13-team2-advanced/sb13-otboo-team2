"""검색 문장을 임베딩하고 로컬 DB에서 관련 코디 문서 3개를 조회한다."""

import json
from getpass import getpass

import psycopg
from sentence_transformers import SentenceTransformer

MODEL_NAME = "intfloat/multilingual-e5-small"

SEARCH_SQL = """
SELECT
    document_key,
    title,
    embedding <=> %s::vector AS distance
FROM style_knowledge
WHERE embedding_model = %s
  AND embedding IS NOT NULL
  AND review_status IN ('DRAFT', 'APPROVED')
ORDER BY distance, document_key
LIMIT 3
"""


def main():
    """저장할 때와 같은 모델로 질문을 변환하고 코사인 거리순으로 검색한다."""
    query = input("검색할 내용: ").strip()

    if not query:
        raise ValueError("검색할 내용을 입력하세요.")

    port = int(input("로컬 DB 포트 [5432]: ").strip() or "5432")
    database = input("DB 이름 [ootd]: ").strip() or "ootd"
    username = input("DB 사용자 [ootd]: ").strip() or "ootd"
    password = getpass("DB 비밀번호: ")

    model = SentenceTransformer(MODEL_NAME)
    vector = model.encode(
        f"query: {query}",
        normalize_embeddings=True,
    )
    vector_text = json.dumps(vector.tolist(), allow_nan=False)

    with psycopg.connect(
        host="127.0.0.1",
        port=port,
        dbname=database,
        user=username,
        password=password,
        connect_timeout=5,
    ) as connection:
        with connection.cursor() as cursor:
            cursor.execute(SEARCH_SQL, (vector_text, MODEL_NAME))
            results = cursor.fetchall()

    for rank, (document_key, title, distance) in enumerate(results, start=1):
        print(f"{rank}위 | 유사도 {1 - distance:.4f} | {document_key}")
        print(f"     {title}")


if __name__ == "__main__":
    main()