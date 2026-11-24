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
python3 ngrinder/recommendation/run_measurement.py probe --port 8081
python3 ngrinder/recommendation/run_measurement.py probe --port 8082
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

```sh
python3 ngrinder/recommendation/run_measurement.py run --mode real --rps 0.1 --threads 1 --runs 3
python3 ngrinder/recommendation/run_measurement.py mock-suite
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
python3 ngrinder/recommendation/run_measurement.py diagnostics --port 8082
```

`diagnostics`는 조회 시점의 지표이며 실행별 증가분이나 전체 구간 모니터링이 아니다.
GC·메모리·연결 풀 대기 등 추가 원인 분석에는 별도 지표나 스레드 덤프를 사용한다.

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
