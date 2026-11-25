# DM STOMP 오류 응답

현재 구현의 통신 계약이다. 저장된 HTTP Swagger 스냅샷(`openapi.json`)에는 STOMP 계약이 포함되어 있지 않다.

- 연결: `/ws` (SockJS), CONNECT 헤더 `Authorization: Bearer <accessToken>`
- 전송: `/pub/direct-messages_send`
- 전송 오류 구독: `/user/sub/direct-messages_errors`

클라이언트는 연결할 때 오류 경로를 구독한 뒤 메시지를 전송한다. 재연결 시에도 다시 구독해야 한다.

```javascript
client.subscribe('/user/sub/direct-messages_errors', (frame) => {
  const error = JSON.parse(frame.body);
  // error.message를 표시하고, details의 필드별 검증 사유를 사용한다.
});
```

업무 예외, 필드 검증 실패, JSON/UUID 변환 실패는 HTTP와 같은 `ErrorResponse` 구조로 응답한다.

```json
{
  "exceptionName": "DirectMessageUserNotFoundException",
  "message": "DM 사용자를 찾을 수 없습니다.",
  "details": {}
}
```

오류는 전송한 세션에만 전달되며, 같은 계정의 다른 연결이나 대화 상대에게 전달되지 않는다. 이 오류 경로는 인증 사용자만 구독할 수 있다. 실제 브로커 목적지로 직접 구독하거나 전송하는 것은 허용하지 않는다.

CONNECT 인증 실패, SEND/SUBSCRIBE 인터셉터의 인증·권한 거부는 컨트롤러에 도달하기 전 발생하므로 이 응답 대상에 포함되지 않는다. 클라이언트는 STOMP ERROR와 연결 종료도 별도로 처리해야 한다. 예기치 않은 서버 오류도 업무 오류 응답으로 변환하지 않는다.

## HTTP 예외 변경

- 없는 DM 사용자: `DirectMessageUserNotFoundException`, HTTP 400.
- 잘못된 DM 커서: `DirectMessageInvalidCursorException`, HTTP 400.
- 잘못된 알림 커서: `NotificationInvalidCursorException`, HTTP 400.
- 없는 알림 또는 본인이 읽을 수 없는 알림: `NotificationNotFoundException`, HTTP 400. 둘을 구분하여 존재 여부를 노출하지 않는다.
- 팔로우 도메인 예외는 기존 상태 코드와 메시지를 유지하며 전용 예외 이름으로 응답한다.

공통 `USER_NOT_FOUND`의 404 처리와 `ErrorResponse` 필드는 변경하지 않는다.
