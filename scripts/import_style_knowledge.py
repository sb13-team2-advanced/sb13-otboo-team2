"""코디 지식 JSON을 임베딩해 로컬 PostgreSQL에 적재하는 명령행 도구.

실행 방법:
    sentence-transformers와 psycopg[binary]가 설치된 Python 환경에서
    python scripts/import_style_knowledge.py 명령으로 실행한다.

입력:
    프로젝트의 docs/recommendation/knowledge/style-guides.draft.json 문서 목록.
    터미널에서 입력하는 DB 포트(기본 5432), DB 이름과 사용자(기본 ootd), 비밀번호.
    비밀번호는 화면에 표시되지 않으며 접속 호스트는 127.0.0.1로 고정된다.

저장 구조:
    pgvector 확장과 style_knowledge 테이블이 미리 생성되어 있어야 한다.
    JSON의 id를 document_key로 사용하고 제목·본문에서 만든 정규화 벡터를
    vector(384)에 저장한다. 분류·태그·필요 속성·의상별 색 배치는 metadata에 저장한다.

실행할 때마다 전체 문서의 임베딩을 계산하며 첫 모델 로딩에는 다운로드가 필요할 수 있다.
검색과 코디 추천은 별도 단계이며 이 도구는 문서와 벡터의 적재를 담당한다.
"""

import json
from getpass import getpass
from pathlib import Path

import psycopg
from psycopg.types.json import Jsonb
from sentence_transformers import SentenceTransformer

# 사용할 모델과 예상 벡터 차원을 지정한다.
MODEL_NAME = "intfloat/multilingual-e5-small"
EMBEDDING_DIMENSIONS = 384

# 현재 파일의 두 단계 위인 프로젝트 루트를 찾는다.
# 이를 기준으로 JSON 경로를 만들기 때문에 실행한 터미널 위치에 덜 영향을 받는다.
PROJECT_ROOT = Path(__file__).resolve().parents[1]
JSON_PATH = (
    PROJECT_ROOT
    / "docs/recommendation/knowledge/style-guides.draft.json"
)

# 처음보는 `document_key`면 새 문서를 저장한다.
# 같은 키가 있으면 기존 문서의 내용과 벡터를 갱신한다.
UPSERT_SQL = """
INSERT INTO style_knowledge (
    document_key, title, content, base_color, combination_type,
    source_type, source_url, review_status, document_version,
    metadata, embedding_model, embedding
)
VALUES (
    %s, %s, %s, %s, %s,
    %s, %s, %s, %s,
    %s, %s, %s::vector
)
ON CONFLICT (document_key) DO UPDATE SET
    title = EXCLUDED.title,
    content = EXCLUDED.content,
    base_color = EXCLUDED.base_color,
    combination_type = EXCLUDED.combination_type,
    source_type = EXCLUDED.source_type,
    source_url = EXCLUDED.source_url,
    review_status = EXCLUDED.review_status,
    document_version = EXCLUDED.document_version,
    metadata = EXCLUDED.metadata,
    embedding_model = EXCLUDED.embedding_model,
    embedding = EXCLUDED.embedding,
    updated_at = CURRENT_TIMESTAMP
"""


"""문서를 검증하고 임베딩한 뒤 하나의 트랜잭션으로 저장하거나 갱신한다.

문서 목록이 비어 있거나 ID가 중복되면 DB에 연결하기 전에 중단한다.
각 문서의 제목과 본문을 passage: 접두어로 결합해 MODEL_NAME 모델에 전달하고,
결과 크기가 문서 수와 EMBEDDING_DIMENSIONS에 맞는지 확인한다.

문서와 벡터를 순서대로 묶어 SQL 매개변수를 구성한다. metadata는 Jsonb로,
벡터는 유한한 숫자의 JSON 배열 문자열로 변환한 뒤 SQL에서 vector로 캐스팅한다.
출처·문서 버전·검토 상태는 JSON의 값을 그대로 반영한다.

document_key가 없으면 삽입하고, 이미 있으면 내용·메타데이터·벡터와 수정 시각을
갱신한다. 기존 행의 UUID와 생성 시각은 유지하며 JSON에서 빠진 문서를 삭제하지 않는다.
저장 중 오류가 발생하면 이번 트랜잭션 전체를 롤백한다. 커밋과 연결 종료가 완료된 뒤
처리한 문서 수를 출력하며 파일 읽기·입력·모델 실행·DB 관련 오류는 호출자에게 전파한다.
"""
def main():

    dataset = json.loads(JSON_PATH.read_text(encoding="utf-8"))
    documents = dataset["documents"]

    if not documents:
        raise ValueError("저장할 문서가 없습니다.")

    document_keys = [document["id"] for document in documents]

    if len(document_keys) != len(set(document_keys)):
        raise ValueError("JSON에 중복 문서 ID가 있습니다.")

    port = int(input("로컬 DB 포트 [5432]: ").strip() or "5432")
    database = input("DB 이름 [ootd]: ").strip() or "ootd"
    username = input("DB 사용자 [ootd]: ").strip() or "ootd"
    password = getpass("DB 비밀번호: ")

    print(f"문서 {len(documents)}개를 임베딩합니다.")

    model = SentenceTransformer(MODEL_NAME)
    texts = [
        f"passage: {document['title']}\n{document['content']}"
        for document in documents
    ]
    vectors = model.encode(
        texts,
        normalize_embeddings=True,
        show_progress_bar=True,
    )

    if vectors.shape != (len(documents), EMBEDDING_DIMENSIONS):
        raise ValueError(f"예상하지 못한 벡터 크기: {vectors.shape}")

    parameters = []

    for document, vector in zip(documents, vectors, strict=True):
        metadata = {
            key: document[key]
            for key in (
                "category",
                "tags",
                "requiredAttributes",
                "outfitVariants",
            )
        }

        parameters.append((
            document["id"],
            document["title"],
            document["content"],
            document["baseColor"],
            document["combinationType"],
            document["sourceType"],
            document.get("sourceUrl"),
            document["reviewStatus"],
            document["version"],
            Jsonb(metadata),
            MODEL_NAME,
            json.dumps(vector.tolist(), allow_nan=False),
        ))

    with psycopg.connect(
        host="127.0.0.1",
        port=port,
        dbname=database,
        user=username,
        password=password,
        connect_timeout=5,
    ) as connection:
        with connection.cursor() as cursor:
            cursor.executemany(UPSERT_SQL, parameters)

    print(f"{len(documents)}개 문서 저장/갱신 완료")


if __name__ == "__main__":
    main()
