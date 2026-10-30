package codeit.ootd.infra.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 파일 삭제 시점을 DB 트랜잭션 결과에 맞춘다.
 * 파일은 롤백으로 되돌릴 수 없으므로, 이전 파일은 커밋이 끝난 뒤에 지우고
 * 새로 올린 파일은 롤백되었을 때만 지운다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionalFileCleaner {

    private final FileStorage fileStorage;

    /**
     * 트랜잭션이 커밋된 뒤에 파일을 삭제한다. 롤백되면 삭제하지 않는다.
     * 활성 트랜잭션이 없으면 바로 삭제한다.
     */
    public void deleteAfterCommit(String fileUrl) {
        if (isBlank(fileUrl)) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            fileStorage.delete(fileUrl);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(fileUrl);
            }
        });
    }

    /**
     * 트랜잭션이 롤백되었을 때 파일을 삭제한다. 새로 업로드한 파일이 DB에 반영되지 못한 경우를 정리한다.
     * 커밋 결과를 알 수 없는 경우(STATUS_UNKNOWN)에는 이미 DB가 참조할 수 있으므로 삭제하지 않는다.
     * 활성 트랜잭션이 없으면 롤백 시점을 알 수 없어 아무것도 하지 않는다.
     */
    public void deleteOnRollback(String fileUrl) {
        if (isBlank(fileUrl) || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    deleteQuietly(fileUrl);
                }
            }
        });
    }

    /**
     * 커밋 후 콜백에서 던진 예외는 이미 끝난 요청의 응답을 깨뜨리므로, 실패해도 로그만 남긴다.
     */
    private void deleteQuietly(String fileUrl) {
        try {
            fileStorage.delete(fileUrl);
        } catch (RuntimeException e) {
            log.warn("파일 삭제 실패: url={}", fileUrl, e);
        }
    }

    private boolean isBlank(String fileUrl) {
        return fileUrl == null || fileUrl.isBlank();
    }
}
