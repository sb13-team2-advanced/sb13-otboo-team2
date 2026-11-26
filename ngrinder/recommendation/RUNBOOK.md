# 의상 추천 API 부하 테스트

## 측정 방식과 한계

현재 구성은 nGrinder의 작업 스레드가 응답을 받은 뒤 다음 요청을 시작하는 방식이다.
스크립트의 `pace()`가 프로세스 전체의 요청 시작 간격을 조절하지만, 응답과 무관하게 일정 RPS를 유지하는 부하 발생기는 아니다.

- Param의 숫자는 **요청 시작 속도의 목표값**이며 달성한 RPS가 아니다.
- 모든 작업 스레드가 응답을 기다리면 다음 요청을 시작하지 못해 목표 속도에 미달한다.
- 지연된 요청을 한꺼번에 따라잡도록 설계하지 않았으며, 실제 시작 간격에는 스케줄링 오차가 있다.
- 목표값, 작업 스레드 수, 실측 유입 속도, 완료 TPS를 함께 기록한다.
- 목표보다 실제 유입이 낮으면 해당 목표 부하를 검증했다고 판단하지 않는다. 예를 들어 `mock_1000` 실행을 곧바로 “100 RPS 처리 성공”으로 기록하면 안 된다.

## 실행 환경

아래 명령은 저장소 루트에서 실행한다.

```sh
docker compose --env-file /dev/null -f ngrinder/recommendation/compose.yaml up -d
```

- Controller: http://127.0.0.1:8300
- 새 설치 기본 로그인: `admin/admin`. 자동 실행 도구도 이 로컬 기본 계정을 사용한다.
- Controller와 Agent: `3.5.9-p1`, ARM Mac에서는 amd64 에뮬레이션 사용
- 실제 Groq용 측정 백엔드: 호스트의 `8081`
- 모의 Groq용 측정 백엔드: 호스트의 `8082`
- 모의 Groq 서버: 호스트의 `8099`

Compose는 Controller와 Agent만 실행한다. 백엔드·DB·Redis·임베딩 서버는 별도로 준비해야 한다.
Agent에서 호스트 백엔드에 접근할 때는 `localhost`가 아닌 `host.docker.internal`을 사용한다.
같은 Mac의 자원을 공유하므로 이 결과를 AWS 서버 성능으로 환산하지 않는다.

## 사전 조건

1. 백엔드의 `dev` 프로필과 `--spring.docker.compose.enabled=false`를 사용하고, 측정용 포트가 기존 서버와 충돌하지 않게 한다.
2. 실제 Groq 백엔드는 `--server.port=8081 --ootd.groq.enabled=true`로 실행한다. 실제 API 키는 기존 로컬 설정을 사용하며 출력하거나 커밋하지 않는다.
3. 임베딩 서버와 검색 문서가 준비되어 있어야 한다. 원피스 추천, 알려진 색상 부족, 일치하는 배색 근거 부족 등으로 Groq 호출이 생략될 수 있다.
4. 오늘 날씨를 사용하면 남은 시간대 예보가 필요하다. 측정 전에 유효한 weatherId와 의상이 있는 테스트 계정을 준비한다.
5. 실제 Groq의 RPM·TPM 등 계정 한도를 확인한다. `x-ratelimit-limit-requests`는 RPD, `x-ratelimit-limit-tokens`는 TPM이며 요청 헤더 한도를 RPM으로
   해석하지 않는다. 확인하지 못한 한도는 미확인으로 기록한다.
6. 429가 발생하면 제한 종류와 기본 설명 대체 수를 확인하고 실제 Groq 부하를 추가로 높이지 않는다. 로컬 실행도 실제 Groq 사용량에 포함된다.
7. 측정 중에는 대상 계정으로 다른 로그인·추천 요청을 하지 않는다. 토큰 만료와 세션 무효화를 확인한다.
8. 서버 지표의 실행 전후 차이를 사용하므로 측정 중 서버를 재시작하거나 다른 추천 트래픽을 섞지 않는다.

### 모의 Groq 준비

```sh
python3 ngrinder/recommendation/mock_groq.py --delay 0.5
```

모의 테스트용 백엔드는 다음 옵션으로 별도 실행한다.

```text
--server.port=8082
--ootd.groq.enabled=true
--ootd.groq.api-key=loadtest-dummy
--ootd.groq.base-url=http://127.0.0.1:8099/openai/v1
```

모의 서버는 전달받은 문서 키·의상 ID와 정상 형식의 설명을 고정 지연 후 반환한다.
DB·임베딩·추천·근거 검증은 실제 경로를 사용하지만 실제 LLM의 속도·품질·한도를 재현하지는 않는다.

## 인증 설정과 스크립트 등록

1. `ngrinder/recommendation/target.properties.example`을 참고해 같은 디렉터리에 `target.properties`를 준비한다.
2. `realBaseUrl`, `mockBaseUrl`, `weatherId`, `accessToken`을 설정한다. 토큰은 `Bearer ` 접두어와 따옴표 없이 값만 넣는다.
3. 실제 토큰 파일은 Git에서 제외하고 예시 파일에는 비밀값을 넣지 않는다.
4. Controller의 Script에서 `RecommendationLoadTest.groovy`를 등록한다.
5. Controller의 같은 스크립트 위치에 `resources` 폴더를 만들고 `resources/target.properties`로 실제 설정 파일을 등록한다.
6. 로컬 토큰 파일을 변경한 뒤에는 Controller의 리소스도 다시 업로드한다. 각 실행에서 최신 스크립트와 리소스 리비전을 사용하는지 확인한다.

단일 요청 검증:

```sh
./gradlew measureRecommendation --args='probe --port 8081'
./gradlew measureRecommendation --args='probe --port 8082'
```

HTTP 200과 출처 존재만으로 실제 Groq 생성 성공을 확정하지 않는다.
`recommendation.explanations`의 `generated:validated` 증가를 함께 확인한다.
`probe`가 출력하는 설명 지표는 서버 시작 이후 누적값이며, 단일 요청 증가분 자체는 아니다.

## 부하 실행

Agent 1, Process 1을 사용하고 Duration 대신 Run Count를 선택한다.
현재 요청 속도 조절 상태는 프로세스 안에서만 공유하므로 프로세스나 Agent를 늘리면 같은 목표값이 각각 적용될 수 있다.
Run Count는 스레드별 반복 횟수다. 이 구성의 예정 요청 수는 `Threads × Run Count`다.

| Param       | 대상             | 요청 시작 속도 목표 |
|-------------|------------------|--------------------:|
| `real_1`    | 실제 Groq        |            0.1회/초 |
| `mock_10`   | 모의 Groq 워밍업 |              1회/초 |
| `mock_83`   | 모의 Groq        |            8.3회/초 |
| `mock_167`  | 모의 Groq        |           16.7회/초 |
| `mock_1000` | 모의 Groq        |            100회/초 |

Param은 `대상_숫자` 형식이며 숫자를 10으로 나눈 값을 사용한다.
워밍업은 별도 실행하고 성능 결과에서 제외한다. 계정 수와 데이터 조건도 함께 기록한다.

### 자동 실행

`RunMeasurement.groovy`는 Gradle에서 실행하고, Agent에 등록한 `RecommendationLoadTest.groovy`가 API에 요청을 보낸다.

```sh
./gradlew measureRecommendation --args='run --mode real --rps 0.1 --threads 1 --runs 3'
./gradlew measureRecommendation --args='mock-suite'
```

`--rps`는 스크립트의 요청 시작 속도 목표를 지정하며, 고정 유입을 보장하는 옵션이 아니다.
`mock-suite`는 다음을 순차 실행한다.

| 단계   | 속도 목표 | Threads | Run Count | 예정 요청 |
|--------|----------:|--------:|----------:|----------:|
| 워밍업 |    1회/초 |       1 |         5 |         5 |
| 측정 1 |  8.3회/초 |      20 |        13 |       260 |
| 측정 2 | 16.7회/초 |      30 |        17 |       510 |
| 측정 3 |  100회/초 |     100 |        30 |     3,000 |

자동 도구는 Controller `127.0.0.1:8300`, 실제 백엔드 `8081`, 모의 백엔드 `8082`를 사용한다.
순차 실행에서는 표본 누락·검증 실패·비정상 종료가 발생하면 다음 단계를 시작하지 않는다.
`--port`는 `probe`와 `diagnostics`에 적용되며 `run`의 지표 수집 포트를 변경하지 않는다.
리소스의 대상 주소를 바꾸면 도구의 고정 지표 수집 주소와 일치하는지 확인해야 한다.

모든 요청이 끝난 후 로그를 수집한다. 최대 600초 내에 끝나지 않으면 도구는 오류로 종료한다.
이 경우 Controller에서 실행 상태를 확인하고 필요하면 직접 중지한다. 도구의 오류 종료가 테스트 자동 중지를 의미하지 않는다.

## 결과 판독

`results/{test_id}-summary.json`과 같은 실행의 로그 ZIP을 보관한다. 결과 디렉터리는 Git에서 제외된다.

| 출력 필드                                         | 의미                                                                                |
|---------------------------------------------------|-------------------------------------------------------------------------------------|
| `target_rps`                                      | 설정한 요청 시작 속도 목표                                                          |
| `expected`, `samples`, `complete`                 | 예정 요청 수, 완료 로그 수, 두 값의 일치 여부                                       |
| `duration_seconds`                                | 첫 요청 시작부터 마지막 응답 완료까지의 시간. 준비 시간 제외, 마지막 응답 대기 포함 |
| `arrival_rps`                                     | 완료 로그의 요청 시작 시각으로 계산한 유입 속도 추정값                              |
| `completed_rps`                                   | 완료 로그 수 / `duration_seconds`                                                   |
| `success_tps`                                     | HTTP 200이고 응답 검증을 통과한 요청 수 / `duration_seconds`                        |
| `errors`                                          | HTTP 실패·전송 실패·응답 검증 실패를 포함한 실패 수. HTTP 오류 수만 의미하지 않음   |
| `mean_ms`, `p50_ms`, `p95_ms`, `p99_ms`, `max_ms` | 수집한 완료 로그 전체의 응답시간 통계                                               |
| `explanations`                                    | 실행 전후 서버의 생성 검증 성공·기본 설명 대체 이유별 카운터 차이                   |
| `ngrinder_status`                                 | Controller가 기록한 실행 종료 상태                                                  |

현재 도구의 `arrival_rps` 계산식은 다음과 같다.

```text
samples / ((마지막 요청 시작 - 첫 요청 시작)의 초 단위 값 + 1 / target_rps)
```

유한 표본의 경계 구간을 목표 간격 하나로 보정한 추정값이다. 서버에서 직접 측정한 유입량이나 고정 유입의 보장이 아니다.
특히 표본이 작으면 완료 RPS와 차이가 커질 수 있다.

latency는 `pace()`의 사전 대기를 제외하고 HTTP 요청 시작부터 응답 파싱·검증까지 측정한다.
성공 요청만이 아니라 전송·검증에 실패한 완료 로그도 포함하며, 백분위수는 nearest-rank로 계산한다.

- `complete=false`, 로그 누락, 준비 단계 실패 또는 중도 종료 결과는 목표 부하 통과 근거로 사용하지 않는다.
- HTTP 200이어도 기본 설명으로 대체될 수 있다. 생성 성공률과 기본 설명 대체 비율을 따로 기록한다.
- 모의 실행의 `generated:validated`는 모의 응답이 서버 검증을 통과했다는 의미이며 실제 LLM 생성 성공률이 아니다.
- 스레드 부족이나 대상 시스템 대기로 실제 유입이 목표에 미달하면 목표 부하를 검증하지 못한 것이다.
- 모든 요청 완료까지 기다리는 방식이므로 시스템이 느려지면 실행 시간도 길어진다. 동일 시간 동안 일정 RPS를 가한 테스트로 해석하지 않는다.

초기 목표는 p95 ≤ 2,000ms, p99 ≤ 3,000ms, API 오류율 < 1%다.
현재 도구의 `errors`는 응답 검증 실패까지 포함하므로 이를 사용할 때는 “HTTP·전송·응답 검증 실패율”로 기록한다.
요청량 달성 여부, 표본 완전성, 생성 성공·대체 비율과 함께 판정한다.

필요하면 다음 명령으로 HTTP 클라이언트 지연·프로세스 CPU·DB 커넥션 대기 지표를 조회한다.

```sh
./gradlew measureRecommendation --args='diagnostics --port 8082'
```

`diagnostics`는 조회 시점의 지표이며 실행별 증가분이나 전체 구간 모니터링이 아니다.
GC·메모리·연결 풀 대기 등 추가 원인 분석에는 별도 지표나 스레드 덤프를 사용한다.

## HTTP 연결 풀 비교

Groq과 임베딩은 각각 독립된 Apache HTTP 연결 풀을 사용한다. 기본값은 각 풀의 전체 최대 25개,
동일 목적지 최대 5개다. Groq 목적지가 하나이면 전체 최대값보다 목적지 최대값이 먼저 적용된다.

같은 JAR·계정·날씨·모의 지연 500ms로 아래 두 구성을 각각 실행한다.
두 실행 사이에는 측정용 백엔드만 재시작하고 임베딩 풀과 제한 시간은 유지한다.

| 설정 | 기준 | Groq 풀 확대 |
|------|-----:|-------------:|
| `ootd.groq.pool.max-total` | 25 | 100 |
| `ootd.groq.pool.max-per-route` | 5 | 100 |
| `ootd.embedding.pool.max-total` | 25 | 25 |
| `ootd.embedding.pool.max-per-route` | 5 | 5 |

연결 획득 대기는 `ootd.groq.pool.acquire-timeout`, `ootd.embedding.pool.acquire-timeout`으로
설정하며 기본값은 기존 Apache 기본값과 같은 `3m`다. 이 비교에서는 대기 제한 시간을 변경하지 않는다.
연결 제한 시간은 각각 2초, 응답 제한 시간은 Groq 8초·임베딩 10초다.
연결 획득 대기는 전체 API 응답 제한 시간과 다르므로 응답 목표 2초를 보장하지 않는다.

```sh
./gradlew measureRecommendation --args='pool-suite --label baseline'
# Groq 풀 크기를 변경하여 측정용 백엔드를 재시작한 다음 실행한다.
./gradlew measureRecommendation --args='pool-suite --label tuned'
```

`pool-suite`는 `mock-suite`와 같은 워밍업·8.3·16.7 목표 단계를 사용한다.
100 목표 단계만 100스레드 × 15회 = 1,500건으로 실행하며 기준·확대 양쪽에 동일하게 적용한다.
최대 100개 작업 스레드를 사용하는 비교 실험이며 서비스 동접 500~1,000명 검증을 의미하지 않는다.

도구는 약 0.5초 간격으로 서버 지표를 조회한다. Prometheus 응답을 받은 시각이 첫 요청 시작과 마지막 응답 완료 사이인 표본만 요약한다.
수집 비용과 간격 오차가 있으며 순간적인 대기를 놓칠 수 있으므로 관측 최대값을 절대 최대값으로 해석하지 않는다.
원본 표본은 `results/{test_id}-resources.json`에 저장한다.

| 지표 / 결과 필드 | 의미 |
|------------------|------|
| `outbound.http.pool.leased` | 사용 중인 연결 수 |
| `outbound.http.pool.pending` | 연결을 받기 위해 대기 중인 요청 수 |
| `outbound.http.pool.available` | 재사용 가능한 유휴 연결 수 |
| `outbound.http.pool.max`, `outbound.http.pool.per.route.max` | 전체·동일 목적지 연결 상한 |
| `pool_metrics` | `client=groq/embedding`별 설정과 사용·대기·유휴의 관측 최대 및 평균 |
| `http_client_mean_ms` | 실행 전후 HTTP 클라이언트 카운터 차이로 계산한 호출 평균 시간. 연결 획득 대기도 포함 |
| `db_pending_max`, `cpu_max` | 측정 구간에서 관측한 DB 연결 대기 수·프로세스 CPU 사용률 최대값 |

Groq 사용 연결 수가 목적지 상한에 도달하고 대기 요청이 쌓이는지 먼저 확인한다.
풀 확대 후 같은 부하에서 대기 수·호출 지연이 줄고 처리량이 증가하면 Groq 풀 제한의 영향을 뒷받침한다.
이후 임베딩 풀이나 CPU가 새 제한이 될 수 있으므로 Groq 풀 확대만으로 전체 병목 해소를 단정하지 않는다.
이 비교는 모의 Groq를 사용하므로 실제 Groq의 호출 한도와 지연 개선을 증명하지 않는다.

### 임베딩 연결 풀 비교

Groq 풀을 전체·목적지별 100개로 고정한 뒤, 임베딩 전체 상한 25개를 유지하면서
`ootd.embedding.pool.max-per-route`만 5·10·20개로 순차 변경한다.
각 설정에서 별도 워밍업과 동일한 `pool-suite`를 실행한다.
오류 발생 또는 처리량 정체와 지연 악화가 관측되면 다음 확대를 진행하지 않는다.

`embedding_observer.py`는 기존 임베딩 이미지의 `/app/scripts/embedding_observer.py`에
읽기 전용으로 마운트해 다음 진입점으로 실행할 수 있다.
모델·입력·반환값은 유지하고 추론과 HTTP 처리의 누적 횟수·시간만 측정한다.

```text
python -m uvicorn scripts.embedding_observer:app --host 0.0.0.0 --port 8001 --workers 1
```

측정 시 같은 이미지·플랫폼·워커 수·CPU·메모리 제한을 유지하고 기존 컨테이너는 잠시 중지한다.
실험 종료 후 임시 컨테이너를 중지하고 기존 컨테이너를 다시 시작한다.

```sh
python3 ngrinder/recommendation/monitor_embedding.py --output ngrinder/recommendation/results/embedding-container-resources.jsonl
./gradlew measureRecommendation --args='pool-suite --label embedding-5 --embedding-metrics-url http://127.0.0.1:8001/_loadtest/metrics'
```

서버를 해당 풀 크기로 재시작한 다음 `--label embedding-10`, `embedding-20`으로 반복한다.
`embedding_server_mean_ms.encode`는 모델 encode 벽시계 시간이며 CPU 연산 시간만을 의미하지 않는다.
`embedding_server_mean_ms.http`는 서버 미들웨어 진입부터 응답 객체 반환까지의 시간으로,
서버 내부 스레드 대기·추론·직렬화 등을 포함하지만 클라이언트 연결 획득 대기와 전체 응답 전송 시간은 포함하지 않는다.
각 count와 완료 요청 수의 일치도 확인한다.
선택 임베딩 지표 수집이 실패해도 백엔드 표본은 저장한다. 임베딩 서버 증가분은 시작과 종료 표본이 모두 있어야 계산한다.
관측한 임베딩 호출 수나 누적 시간이 감소하면 `run` 요약에서 임베딩 서버 지표를 생략하고 `probe`의 임베딩 서버 호출 수를 `null`로 표시한다.

자원 수집기는 Docker CPU·메모리 표본을 JSONL로 저장한다. CPU 100%는 약 1코어 사용에 해당하며,
메모리는 Docker가 보고한 작업 집합 값이다. 각 테스트의 실제 요청 시간 구간에 해당하는 표본끼리 비교한다.
로컬 Docker의 CPU·메모리 및 에뮬레이션 조건은 운영 서버 성능과 다를 수 있다.

## 임베딩 벡터 캐시 검증

추천 경로는 `StyleKnowledgeSearchService → EmbeddingService → EmbeddingClient`로 구성한다.
벡터만 JVM 내 Caffeine에 저장하며 DB 문서 검색·최종 추천·Groq 응답은 캐시하지 않는다.
여러 백엔드는 각각 캐시를 보유하므로 최초 추론이 서버별로 발생할 수 있으며 분산 요청 병합은 제공하지 않는다.

| 설정 | 기본값 | 의미 |
|------|--------|------|
| `ootd.embedding.cache.enabled` | true | false이면 캐시와 요청 병합을 우회해 같은 빌드의 기준 성능 측정 |
| `ootd.embedding.cache.maximum-size` | 512 | 최대 벡터 항목 수 |
| `ootd.embedding.cache.ttl` | 24h | 저장 시점부터의 만료 시간 |
| `ootd.embedding.cache.model-revision` | v1 | 운영자가 모델 가중치 변경 시 갱신하는 캐시 버전 |
| `ootd.embedding.cache.preprocessing-version` | query-normalized-v1 | query 접두어·정규화·토큰화 등 변경 시 갱신하는 버전 |

캐시 키는 모델명·위 두 버전·앞뒤 공백을 제거한 문장을 함께 사용한다.
버전 값은 모델 서버의 revision을 자동 검출하거나 가중치를 고정하는 기능이 아니다.
모델·전처리 변경 배포 시 버전을 함께 변경하고 문서 벡터와의 호환성도 확인한다.
같은 JVM에서 동일 키로 기다리는 요청은 한 임베딩 호출의 결과나 예외를 공유한다. 실패 이후 들어온 요청은 다시 시도할 수 있다.
공유 벡터는 불변 복사본으로 저장한다.

재측정은 다음 조건을 분리한다.

1. 재시작 후 빈 캐시의 첫 요청: 실제 임베딩 호출과 미스 증가 확인.
2. 워밍업 후 반복 요청: 벡터 재사용과 임베딩 호출 감소 확인.
3. 다른 의상 종류·색상의 검색 문장이 섞인 요청: 실제 적중률과 처리량 확인.

Actuator의 `cache.gets`에 `cache=embeddingVectors`, `result=hit/miss` 태그를 사용한다.
로드 성공·실패는 `cache.load`의 `result=success/failure`, 누적 로드 시간은 `cache.load.duration`, 퇴출은 `cache.evictions`에서 확인한다.
실행기는 이 지표의 증가분을 `embedding_cache`에 저장한다. `load_seconds`는 누적 로드 시간의 증가분을 초로 표시한다.
적중률은 적중·미스만으로 계산하며, 진행 중인 호출을 공유한 대기 요청은 별도 적중·미스로 집계하지 않는다.
`http_client_calls`에는 실제 외부 호출 증가분을 저장한다.
같은 계정·날씨 반복 테스트만으로 운영 적중률을 추정하지 않는다.

### 2026-11-26 캐시 OFF/ON 재측정

동일 JAR에 `--ootd.embedding.cache.enabled=false/true`만 변경했다.
Groq은 모의 지연 500ms·풀 100/100, 임베딩은 실제 서버·풀 25/5를 유지했다.
각 설정 전에 Controller·Agent와 측정 백엔드를 재시작하고 단일 요청 확인·별도 워밍업 후
20스레드×13회, 30스레드×17회, 100스레드×15회를 순차 실행했다.
첫 시도의 #33은 Agent 메모리 부족 보호로 중단되어 제외했으며, 보호 설정은 해제하지 않았다.
재시작 후 완료된 OFF #66·67·68과 ON #98·99·100을 비교한다.

| 목표 | OFF 완료 TPS / p95 | ON 완료 TPS / p95 |
|------|-------------------|------------------|
| 8.3 RPS | 8.181 / 661.914ms | 8.212 / 542.368ms |
| 16.7 RPS | 16.433 / 665.181ms | 16.480 / 532.317ms |
| 100 RPS | 31.666 / 3370.030ms | 53.614 / 8276.046ms |

ON의 첫 단일 요청은 935ms, 캐시 미스·임베딩 HTTP 호출·서버 추론 각각 1회였다.
그 뒤 워밍업과 측정에서는 모두 적중해 실제 임베딩 호출이 0회였다.
OFF/ON 비교의 측정 요청 4,540건은 모두 성공했지만 100 RPS 목표는 여전히 미달했다.
ON #100에서 첫 100건에 긴 지연이 집중됐으며 전체 p99는 11043.384ms였다.
이후 구간만 골라 전체 결과를 대체하지 않는다.

같은 조건의 추가 진단 #101에서도 53.489 TPS, p95 7034.814ms로 긴 지연이 재현됐다.
이 실행의 서버 관측 평균은 527.444ms, 임베딩 호출·Groq 풀 대기·DB 풀 대기는 0이었다.
부하 발생기·연결 초기화 등 서버 관측 밖의 지연 가능성이 있으나 원인은 확정하지 않았다.
캐시 재사용과 임베딩 부하 감소는 확인됐지만 고부하 p95 개선 및 100 RPS 통과 근거로 사용하지 않는다.
ON #100의 nGrinder 화면은 TPS 99.4, 평균 679.54ms로 표시됐다.
화면 통계와 외부 PERF 타이머의 측정 시작·집계 구간이 다르므로 위 완료 TPS·p95와 혼용하지 않는다.
이 차이는 초기 부하 발생기·연결 준비 구간을 추가 조사할 근거이며, 초기 요청을 임의로 제외할 근거는 아니다.
상세 비교는 Git 제외 파일 `results/cache-comparison.json`에 보관한다.

테스트 실행이 시작되면 `results/{test_id}-controller.json`에 마지막 Controller 상태를 저장한다. 지표 수집에 실패한 경우 `metrics_errors`의 `source`로 어느 서버의 지표를 읽지 못했는지 확인할 수 있다.
종료 상태를 확인한 뒤 지표 수집 결과와 관계없이 Agent 로그를 내려받으며, 로그 목록은 최대 10회 재조회한다.
종료 후 지표를 읽지 못하면 요청 로그 기반 TPS와 지연 시간은 집계하고, 종료 시점 카운터가 필요한 지표는 `null`로 표시하거나 생략한다.
`api_server_mean_ms`는 서버의 추천 API 타이머 증가분으로 계산한 평균이며
부하 발생기부터의 전체 지연이나 서버 p95를 의미하지 않는다.

### 초기 느린 요청 추적

모의 테스트 백엔드 실행 인자에 Tomcat 접근 로그를 추가한다. 로그 디렉터리는 절대 경로를 사용한다.
로그에 인증 헤더·쿼리 문자열은 포함하지 않는다.

```text
--server.tomcat.accesslog.enabled=true
--server.tomcat.accesslog.directory=<저장소의 절대 경로>/ngrinder/recommendation/results
--server.tomcat.accesslog.prefix=tail-cache-on
--server.tomcat.accesslog.rotate=false
--server.tomcat.accesslog.suffix=.access.log
'--server.tomcat.accesslog.pattern=%{X-Request-ID}i %{begin:msec}t %{end:msec}t %{us}T %F %s %U'
```

부팅 뒤 단일 요청과 워밍업을 수행한다. Controller에 최신 Groovy 스크립트를 등록하고 실행한다.

```sh
./gradlew measureRecommendation --args='run --trace --rps 100 --threads 100 --runs 15 --label cache-on-tail'
```

로그 ZIP의 `TRACE id`를 Tomcat 로그의 `X-Request-ID`와 대조한다. `httpUs`에서 Tomcat 처리시간을
빼면 Tomcat 측정 구간 밖의 경과 시간을 추정할 수 있고, `totalUs - httpUs`는 본문 변환 이후의 검증 시간이다.
nGrinder 3.5.9-p1의 `GET`은 응답 본문 바이트가 수신된 뒤 반환한다.
두 시스템의 벽시계를 빼서 얻은 시차는 시계 동기화와 오차를 확인한 경우에만 사용한다.
첫 100건과 나머지 요청을 진단용으로 나눠 보고 전체 p95·p99도 함께 유지한다.
정상 비교에서는 OFF/ON 모두 실제 유입이 약 30 RPS인지 확인하고, 동일한 요청 수와 스레드 설정을 사용한다.

이전 ON 실행 #100·101을 다시 확인했을 때 Agent가 100번째 HTTP 응답을 기록한 뒤
첫 `PERF` 레코드가 나오기까지 같은 Agent 시계로 각각 4.128초·7.102초가 걸렸다.
HTTP 클라이언트의 응답 로그는 본문 바이트 수신 뒤에 기록된다. 당시 로그에는 요청 ID가 없어
개별 요청별 수치는 알 수 없으나, 이 시간차는 Agent의 응답 수신 이후 구간을 조사할 근거다.

## 종료

```sh
docker compose --env-file /dev/null -f ngrinder/recommendation/compose.yaml stop
```

별도로 실행한 측정용 백엔드와 모의 서버도 해당 실행 터미널에서 종료한다.

## 로컬 컴파일 확인

`build.gradle`의 `sourceSets.ngrinder.groovy.srcDir`가 현재 디렉터리인 `ngrinder/recommendation`을 가리켜야 스크립트가 컴파일 대상에 포함된다.
이전 경로 `performance/ngrinder`가 남아 있으면 Gradle 빌드 성공만으로 현재 스크립트 컴파일을 확인할 수 없다.
실제 Agent 실행 검증도 별도로 필요하다.

## 참고

- [nGrinder HTTP 클라이언트](https://github.com/naver/ngrinder/wiki/The-New-nGrinder-HTTP-Client)
- [Groq 호출 한도와 응답 헤더](https://console.groq.com/docs/rate-limits)
