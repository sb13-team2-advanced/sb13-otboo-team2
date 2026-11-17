# 팀 공용 Grafana

별도 EC2 한 대에서 Grafana OSS와 Caddy를 실행한다. 팀원은 동일한 HTTPS
주소에 접속하고 각자의 Grafana 계정으로 로그인한다. 기존 로컬 Grafana와
별개이며, 로컬 DB나 액세스 키를 공용 서버로 복사하지 않는다.

## 구성과 비용

- 서울 리전 `t4g.small` 1대, 암호화된 gp3 20 GiB, Elastic IP 1개.
- 기존 퍼블릭 서브넷과 인터넷 게이트웨이를 사용한다.
- 외부 포트는 80(인증서 검증/HTTPS 이동), 443(로그인)이며 22와 3000은 열지 않는다.
- EC2 IAM 역할로 CloudWatch 지표와 지정한 AMP 워크스페이스만 조회한다.
- 관리 접속은 Systems Manager Session Manager를 사용한다.
- 회원가입과 익명 접근은 비활성화하고, 기본 조직 권한은 Viewer로 지정한다.
- 공인 IP 인증서를 Let's Encrypt `shortlived` 프로필로 발급하고 Caddy가 갱신한다.
  인증서 발급과 갱신을 위해 80/443의 외부 접근이 필요하다.
- 공인 IP 접속 시 SNI가 없는 클라이언트도 올바른 인증서를 받도록 Caddy의
  `default_sni`를 공인 IP로 지정한다. Docker 내부 IP에 대한 인증서를 찾는
  TLS 연결 오류를 방지한다.
- `grafana-data` 볼륨은 계정과 대시보드를, `caddy-data`는 인증서를 보관한다.
- AMI는 명시적인 ID로 고정한다. 일반 설정 갱신 시 AMI를 다시 선택하지 않는다.
- 루트 EBS는 `DeleteOnTermination=false`로 보존한다. EC2가 교체돼도 기존
  디스크는 남지만 새 서버로 자동 연결되지는 않으므로 아래 복구 절차를 따른다.

2026-11-16 공식 AWS Pricing API 조회 기준, 730시간 운용 시 EC2는
`0.0208 * 730 = $15.184`, gp3는 `0.0912 * 20 = $1.824`, 공인 IPv4는
`0.005 * 730 = $3.65`로 기본 합계 약 **$20.66/월**이다. 세금, 전송량,
CloudWatch/AMP 조회·보관 사용량과 백업 비용은 별도다. 무료 크레딧은 제외했다.
CPU 크레딧은 standard로 설정해 추가 CPU 크레딧 과금을 피한다.

## 배포 준비

프로젝트 루트에서 실행한다. 이 스크립트는 명시한 설정 파일만 읽으며 AWS
리소스를 생성하거나 자격 증명 파일을 읽지 않는다.

```bash
python3 monitoring/shared/prepare_stack.py --output /tmp/otboo-grafana-stack.json
aws cloudformation validate-template \
  --profile otboo-dev --region ap-northeast-2 \
  --template-body file:///tmp/otboo-grafana-stack.json
```

CloudFormation의 `ImageId`에는 Parameter Store 경로가 아닌 실제 `ami-...` ID를
입력한다. 신규 서버용 Amazon Linux 2023 ARM64 AMI는 아래처럼 한 번 조회한다.
조회 결과를 기록하고 이후 일반 스택 업데이트에서는 같은 AMI ID를 유지한다.

```bash
grafana_image_id=$(aws ssm get-parameter \
  --profile otboo-dev --region ap-northeast-2 \
  --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64 \
  --query Parameter.Value --output text)
```

생성할 리소스와 비용을 검토한 뒤 배포한다. EC2의 최초 시작 스크립트는
고정 버전 Docker Compose를 내려받아 체크섬을 검증하고 Grafana를 실행한다.
관리자 비밀번호는 서버 안에서 무작위로 생성하며 템플릿이나 출력에 넣지 않는다.

```bash
aws cloudformation create-stack \
  --profile otboo-dev --region ap-northeast-2 \
  --stack-name otboo-shared-grafana \
  --template-body file:///tmp/otboo-grafana-stack.json \
  --parameters \
    ParameterKey=VpcId,ParameterValue=vpc-01742dfaf7f5d4a54 \
    ParameterKey=SubnetId,ParameterValue=subnet-05c55f209b7c63d3d \
    ParameterKey=ImageId,ParameterValue="$grafana_image_id" \
  --capabilities CAPABILITY_IAM \
  --tags Key=Project,Value=otboo Key=Purpose,Value=monitoring
```

CloudFormation 생성 완료는 컨테이너와 TLS의 준비 완료를 보장하지 않는다.
출력의 InstanceId로 Session Manager에 접속해서 `cloud-init status --wait`,
`docker ps`, `docker logs`로 확인한 뒤, 출력 GrafanaUrl의 `/api/health`가
인증서 오류 없이 HTTP 200을 반환하는지 별도로 확인한다.

## 기존 스택에 AMI 고정·디스크 보존 설정 반영

파일 수정만으로 실행 중인 AWS 서버의 설정이 바뀌지는 않는다. 기존 스택을
전환할 때에는 먼저 EBS 스냅샷을 확보하고, **현재 서버가 사용하는 AMI ID**를
명시해야 한다. 최신 AMI를 새로 조회해 넣으면 의도치 않은 서버 교체가 발생할 수 있다.

```bash
grafana_instance_id=$(aws cloudformation describe-stacks \
  --profile otboo-dev --region ap-northeast-2 --stack-name otboo-shared-grafana \
  --query 'Stacks[0].Outputs[?OutputKey==`InstanceId`].OutputValue | [0]' --output text)
grafana_image_id=$(aws ec2 describe-instances \
  --profile otboo-dev --region ap-northeast-2 --instance-ids "$grafana_instance_id" \
  --query 'Reservations[0].Instances[0].ImageId' --output text)

aws cloudformation create-change-set \
  --profile otboo-dev --region ap-northeast-2 \
  --stack-name otboo-shared-grafana \
  --change-set-name pin-ami-and-preserve-disk \
  --change-set-type UPDATE \
  --template-body file:///tmp/otboo-grafana-stack.json \
  --parameters \
    ParameterKey=VpcId,UsePreviousValue=true \
    ParameterKey=SubnetId,UsePreviousValue=true \
    ParameterKey=ImageId,ParameterValue="$grafana_image_id" \
  --capabilities CAPABILITY_IAM
```

변경 세트에서 EC2 `Instance`가 `Replacement=False`인지 확인한 뒤 적용한다.
UserData 변경으로 재시작이 발생할 수 있지만 초기화 스크립트는 다시 실행되지 않는다.
적용 후 EC2 스토리지 화면에서 「종료 시 삭제」가 비활성화됐는지 확인한다.
애플리케이션 설정 파일은 다음 SSM 절차로 별도 반영한다.

## 기존 서버의 설정 갱신

Compose·Caddy·데이터 소스·대시보드 변경에는 `prepare_update.py`를 사용한다.
UserData 변경이나 초기화 스크립트 재실행에 의존하지 않고 SSM으로 명시적으로 적용한다.
관리자 비밀번호가 없으면 중단하며, 비밀번호를 생성하거나 덮어쓰지 않는다.

```bash
python3 -B monitoring/shared/prepare_update.py \
  --address 52.78.212.1 --output /tmp/otboo-grafana-update.json

aws ssm send-command \
  --profile otboo-dev --region ap-northeast-2 \
  --instance-ids "$grafana_instance_id" \
  --document-name AWS-RunShellScript \
  --parameters file:///tmp/otboo-grafana-update.json \
  --comment 'Update Grafana configuration with backups'
```

생성 스크립트는 로컬에서 JSON만 만들며 AWS에 작업을 실행하지 않는다.
전송 전 생성 파일을 확인한다. `grafana_instance_id`는 위의 읽기 전용 명령으로
조회하고, 공용 IP는 CloudFormation 출력과 일치시킨다.

SSM 갱신 처리는 다음 순서로 실행된다.

1. 갱신 잠금을 획득하고 후보 파일을 임시 디렉터리에 배치한다.
2. Compose 설정 검사, 이미지 다운로드, 후보 Caddy 이미지로 설정 검증을 수행한다.
3. 기존 설정과 관리자 비밀번호를 root 전용 백업에 보관한다.
4. 컨테이너를 중지하고 Grafana·Caddy의 모든 데이터 볼륨을 백업한다.
5. 설정 파일을 교체하고 기존 데이터 볼륨으로 컨테이너를 재생성한다.
6. 인증서 검증을 유지한 HTTPS `/api/health` 요청의 성공을 확인한다.

설정 검증에 실패하면 기존 서비스를 중지하지 않고 설정 파일도 변경하지 않는다.
중지 이후 오류가 나면 컨테이너 시작을 시도하지만, DB 마이그레이션 가능성이 있어
자동으로 이미지를 다운그레이드하지 않는다. SSM 실패 상태와 로그를 반드시 확인한다.
갱신 중에는 짧은 서비스 중단이 발생한다.

백업 위치는 `/var/backups/otboo-grafana/update-...`이다. 내용은 `config.tar.gz`,
`admin-password`, `grafana-data.tar.gz`, `caddy-data.tar.gz`, `caddy-config.tar.gz`다.
비밀번호가 포함되므로 서버 외부에 공개하지 않는다. 로컬 백업은 디스크 용량을
사용하므로 복구 확인 후 보관 정책에 따라 정리한다.

## 갱신 실패·서버 교체 시 복구

1. 복구 대상 볼륨 ID와 백업 시점을 확인하고 필요하면 스냅샷을 추가로 만든다.
   이전 서버와 새 서버의 볼륨을 구분한다.
2. 서버 교체 시 보존된 이전 EBS 또는 스냅샷에서 만든 볼륨을 같은 AZ의 복구용
   서버에 추가 연결해 마운트한다. 기존 파일 시스템을 포맷하지 않는다.
3. 이전 볼륨의 `/var/backups/otboo-grafana`에서 백업 일체를 확보한다. 갱신 전
   백업이 없으면 이전 디스크의 스냅샷을 보존하고 Docker 데이터·설정·인증 정보를
   회수하는 절차를 확인한 뒤 복구한다.
4. 복구할 서버의 Grafana·Caddy를 중지하고 `config.tar.gz`와 `admin-password`를
   원래 경로에 복원한다. 새 서버라면 공용 IP와 파일 소유자도 확인한다.
5. `docker volume inspect`로 복구 대상 볼륨을 확인하고 비어 있는 대상 볼륨에
   해당 데이터 아카이브를 복원한다. `grafana-data`는 DB와 플러그인을 포함해
   같은 시점으로 복원하며, 현재 데이터 위에 덮어쓰지 않는다.
6. 복원한 Compose의 이미지로 `up -d --force-recreate`를 실행하고 HTTPS,
   로그인, 기존 계정과 대시보드를 확인한다.

기존 서버에서 초기화 스크립트를 다시 실행하는 방식으로 복구하지 않는다.
AMI를 의도적으로 바꿀 때에도 백업과 이 복구 계획을 먼저 준비한다.

## 로그인과 팀원 추가

1. 관리자가 AWS 콘솔의 EC2 → `otboo-grafana` → 연결 → Session Manager로 접속한다.
2. 서버 터미널에서 `sudo cat /etc/otboo-grafana/admin-password`로 최초 비밀번호를
   직접 확인한다. 채팅이나 Git에 붙이지 않는다.
3. 공용 HTTPS 주소에 `admin`과 해당 비밀번호로 로그인한다.
4. Grafana 사용자 관리에서 팀원별 계정을 생성하거나 초대 링크를 발급한다.
   메일 전송은 설정되어 있지 않으므로 초대 링크를 각 팀원에게 직접 전달한다.
5. 조회만 필요한 팀원은 Viewer, 대시보드를 편집할 팀원은 Editor를 부여한다.
   공용 admin 계정을 공유하지 않는다.

UI에서 비밀번호를 바꾸면 서버의 최초 비밀번호 파일과 달라질 수 있다.
이미 생성된 사용자의 비밀번호는 컨테이너 재시작으로 재설정되지 않는다.

## 지표 확인

- AWS 폴더의 `otboo-ecs-monitoring`에 서비스 CPU와 메모리 패널을 준비한다.
  각 지표와 화면 갱신은 1분 단위이며 지표 게시 지연이 있을 수 있다.
- `aws-cloudwatch` 연결 검사는 지표 조회가 성공해도 로그 권한 부족을 표시할 수 있다.
  이 구성은 `logs:DescribeLogGroups`를 포함하지 않는 지표 전용 역할이다.
- `aws-prometheus`의 Explore에서 `up{job="otboo"}`로 수집을 확인한다.
  1은 수집 성공, 0은 수집 실패이며 No data는 시계열이 없는 상태다.
- 애플리케이션 `/actuator/prometheus`의 401 응답은 Grafana 배포와 별도로 해결한다.
  JVM/HTTP 지표는 해당 지표를 노출하는 애플리케이션 이미지 배포 후 확인한다.

### JVM·HTTP 대시보드

AWS 폴더의 `otboo-application-monitoring`은 AMP의 `otboo` job과
`otboo-backend` 태스크 패밀리, `otboo-dev-cluster` 클러스터를 조회한다.
수집 상태, 정상 대상 수, HTTP 요청률·5xx 비율, JVM 힙·GC 정지 시간·스레드,
상태 코드별 요청률, 평균 응답 시간 패널을 제공한다. 태스크별 JVM 지표는
`aws_ecs_task_id`로 구분하므로 배포 중에도 서로 다른 JVM을 합치지 않는다.

HTTP·GC 패널은 최근 5분의 변화율을 사용한다. HTTP 지표에서 `/actuator` 요청은
제외한다. 시작 직후 표본이 부족하거나 실제 요청이 없으면 HTTP 패널에 No data가
표시될 수 있다. 수집 상태 패널이 1인지 별도로 확인한다. 평균 응답 시간은
요청 시간 합계를 요청 수로 나눈 값이며 p95·p99가 아니다.

실제 ECS 배포에는 ADOT 사이드카가 있다. Parameter Store의
`/otboo/monitoring/adot-config`를 `AOT_CONFIG_CONTENT`로 받아
`localhost:8080/actuator/prometheus`를 30초마다 수집하고 AMP로 전송한다.
로컬 `monitoring/prometheus.yml`은 이 ECS 수집 설정에 적용되지 않는다.
AMP 연결 검사 성공과 `up=1`을 각각 확인해야 한다. ADOT 로그의 401은 앱
이미지의 SecurityConfig·Prometheus 레지스트리·엔드포인트 노출 설정을 점검한다.

### 준비된 알림 규칙

`provisioning/alerting/otboo.json`에는 다음 다섯 규칙을 준비한다.
수신 채널이 정해지지 않은 상태이므로 모두 `isPaused=true`로 배포하며,
계정·연락처·알림 라우팅은 생성하거나 변경하지 않는다.

| 규칙 | 조건 | 지속 시간 |
| --- | --- | --- |
| ECS CPU | 80% 초과 | 5분 |
| ECS 메모리 | 80% 초과 | 5분 |
| 앱 지표 수집 실패 | 수집 값 0 또는 현재 시계열 없음 | 2분 |
| HTTP 5xx 비율 | 5분간 20건 이상 요청 중 5% 초과 | 5분 |
| HTTP 평균 응답 시간 | 5분간 20건 이상 요청에서 1초 초과 | 5분 |

수신 채널을 정한 뒤 Grafana에서 Contact point와 Notification policy를 구성하고,
전송 테스트를 확인한 다음 파일의 `isPaused`를 false로 바꿔 재배포한다.
파일로 프로비저닝한 규칙 변경은 이 파일에 반영한다. HTTP 무트래픽은 정상으로
처리하며, 메트릭 수집 누락은 별도 수집 실패 규칙으로 감시한다. CPU·메모리의
No data와 모든 규칙의 조회 오류는 각각 NoData·Error로 구분한다.

### 2026-11-17 앱 수집 복구 기록

운영 `otboo-backend:15`의 ADOT 수집기는 `/actuator/prometheus`에서 401을
받았다. 당시 운영 소스인 `3bb49cc764429330d45a833d1d7060fd6d0aebac`를
별도 디렉터리로 추출하고 [최소 수정 패치](production-metrics-hotfix.patch)만
적용했다. Prometheus 레지스트리 추가, 운영 엔드포인트 노출, 익명 GET 수집 허용,
application 메트릭 태그 설정을 포함한다. 현재 브랜치의 다른 기능은 포함하지 않았다.

- ECR 태그: `metrics-3bb49cc-20261117-v1`
- 배포 이미지: `121246721746.dkr.ecr.ap-northeast-2.amazonaws.com/otboo-backend@sha256:1ddb51f25ae5d944a49695ec8b13d5c3e2fa78866b2a206add22fb6552c9fa79`
- ECS 태스크 정의: `otboo-backend:16`, 기존 정의: `otboo-backend:15`
- ADOT·태스크 역할·환경 변수·시크릿·네트워크 설정은 기존 정의를 유지했다.
- 운영 수정본 전체 테스트 967개, 클린 빌드와 커버리지 검증을 통과했다.
- 새 태스크의 ALB healthy, ECS COMPLETED, AMP `up=1`, JVM·HTTP 시계열을 확인했다.
- `/api/auth/csrf` 조회가 HTTP 200으로 응답하는 것을 확인했다. 토큰은 기록하지 않았다.
- Grafana의 JVM·HTTP 대시보드와 일시정지된 알림 규칙 5개를 반영하고 실제
  데이터 소스 및 조건 쿼리가 오류 없이 실행되는 것을 확인했다.
- Grafana 갱신 전 설정·비밀번호·데이터 볼륨 백업:
  `/var/backups/otboo-grafana/update-20261117T055018Z.k4ANim`.

롤백이 필요하면 ECS 서비스를 기존 `otboo-backend:15`로 되돌릴 수 있지만
이전 버전의 메트릭 수집은 다시 실패한다. 이후 정식 앱 배포에서는 현재 소스의
동일한 메트릭 구성과 `PrometheusSecurityIntegrationTest`를 유지한다.

프로비저닝한 대시보드는 UI에서 편집·저장할 수 있지만 파일 갱신 시 다시 덮어쓸 수
있다. 영구 변경은 JSON에 반영하거나 다른 UID로 복제한 대시보드에 저장한다.

## 유지보수와 종료

컨테이너의 restart 정책으로 EC2 재부팅 후 다시 실행된다. Compose 명령을
직접 실행할 때에는 `GRAFANA_ADDRESS`를 실제 고정 IP로,
`GRAFANA_ADMIN_PASSWORD_FILE`을 `/etc/otboo-grafana/admin-password`로 설정하고
`--env-file /dev/null -f /opt/otboo-grafana/compose.yaml`을 지정한다.

이미지 버전 변경 시 기존 데이터 볼륨을 보관하고 백업 후 업그레이드한다.
특히 Grafana 버전 업그레이드가 DB 마이그레이션을 수행하면 단순 이미지
다운그레이드로 되돌릴 수 없으므로 EBS 스냅샷이나 중지 상태의 볼륨 백업이 필요하다.

스택 삭제 또는 EC2 교체 전 Grafana를 중지하고 EBS 스냅샷을 만들어 계정,
대시보드와 인증서를 백업한다. **수정된 템플릿은 서버 종료 후에도 루트 EBS를 보존한다.**
새 서버에 데이터가 자동 이전되는 것은 아니며 위 복구 절차가 필요하다.
EC2 정지만으로는 디스크와 Elastic IP 비용이 사라지지 않는다. 스택 삭제 후에도
보존된 EBS와 스냅샷은 과금되므로, 복구 필요성을 확인한 뒤 볼륨 ID를 대조해
수동 정리한다. 기존 스택은 보존 설정을 실제 반영하기 전까지 기존 삭제 정책을 따른다.

## 로컬 검증

```bash
python3 -B -m unittest discover -s monitoring/shared -p 'test_*.py' -v
```

Docker·HTTPS 명령을 대체한 임시 디렉터리에서 비밀번호 유지, 데이터 백업,
새 설정 파일 추가, 검증 실패 시 미적용을 확인한다. 설정 파일은 gzip으로 압축해
EC2의 16 KiB UserData 제한 안에 포함하며 서버에서 원본으로 복원한다.
실제 AWS 스택 업데이트·볼륨 복구 검증은 별도다.

## 참고 문서

- [Grafana Docker 설정](https://grafana.com/docs/grafana/latest/setup-grafana/configure-docker/)
- [Grafana 프로비저닝](https://grafana.com/docs/grafana/latest/administration/provisioning/)
- [Grafana AMP 인증](https://grafana.com/docs/plugins/grafana-amazonprometheus-datasource/latest/configure/)
- [컨테이너와 EC2 IMDS 설정](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/configuring-IMDS-new-instances.html)
- [Let's Encrypt 공인 IP 인증서](https://letsencrypt.org/2026/01/15/6day-and-ip-general-availability)
- [AWS 공인 IPv4 요금](https://aws.amazon.com/vpc/pricing/)
- [EC2 종료 시 EBS 보존](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/preserving-volumes-on-termination.html)
- [SSM Run Command](https://docs.aws.amazon.com/systems-manager/latest/userguide/run-command.html)
