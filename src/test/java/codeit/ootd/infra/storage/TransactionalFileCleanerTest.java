package codeit.ootd.infra.storage;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class TransactionalFileCleanerTest {

    private static final String URL = "/files/clothes/a.png";

    @Mock
    private FileStorage fileStorage;

    private TransactionalFileCleaner cleaner;

    @BeforeEach
    void setUp() {
        clearSynchronization();
        cleaner = new TransactionalFileCleaner(fileStorage);
    }

    @AfterEach
    void tearDown() {
        clearSynchronization();
    }

    @Test
    void 활성_트랜잭션이_없으면_커밋_후_삭제는_바로_삭제한다() {
        cleaner.deleteAfterCommit(URL);

        verify(fileStorage).delete(URL);
    }

    @Test
    void 활성_트랜잭션이_없으면_롤백_시_삭제는_아무것도_하지_않는다() {
        cleaner.deleteOnRollback(URL);

        verifyNoInteractions(fileStorage);
    }

    @Test
    void 커밋_후_삭제는_커밋_전에는_삭제하지_않고_커밋_뒤에_삭제한다() {
        TransactionSynchronizationManager.initSynchronization();

        cleaner.deleteAfterCommit(URL);
        verifyNoInteractions(fileStorage);

        commit();

        verify(fileStorage).delete(URL);
    }

    @Test
    void 커밋_후_삭제는_롤백되면_삭제하지_않는다() {
        TransactionSynchronizationManager.initSynchronization();

        cleaner.deleteAfterCommit(URL);
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        verifyNoInteractions(fileStorage);
    }

    @Test
    void 롤백_시_삭제는_롤백되면_삭제한다() {
        TransactionSynchronizationManager.initSynchronization();

        cleaner.deleteOnRollback(URL);
        verifyNoInteractions(fileStorage);

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(fileStorage).delete(URL);
    }

    @Test
    void 롤백_시_삭제는_커밋되면_삭제하지_않는다() {
        TransactionSynchronizationManager.initSynchronization();

        cleaner.deleteOnRollback(URL);
        commit();

        verify(fileStorage, never()).delete(any());
    }

    @Test
    void 롤백_시_삭제는_결과를_알_수_없으면_삭제하지_않는다() {
        TransactionSynchronizationManager.initSynchronization();

        cleaner.deleteOnRollback(URL);
        complete(TransactionSynchronization.STATUS_UNKNOWN);

        verify(fileStorage, never()).delete(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void 대상이_없으면_아무것도_등록하지_않는다(String url) {
        TransactionSynchronizationManager.initSynchronization();

        cleaner.deleteAfterCommit(url);
        cleaner.deleteOnRollback(url);

        assertThatCode(() -> {
            commit();
            complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        }).doesNotThrowAnyException();
        verifyNoInteractions(fileStorage);
    }

    @Test
    void 커밋_후_삭제가_실패해도_예외를_던지지_않는다() {
        doThrow(new IllegalStateException("disk")).when(fileStorage).delete(URL);
        TransactionSynchronizationManager.initSynchronization();

        cleaner.deleteAfterCommit(URL);

        assertThatCode(this::commit).doesNotThrowAnyException();
    }

    @Test
    void 롤백_시_삭제가_실패해도_예외를_던지지_않는다() {
        doThrow(new IllegalStateException("disk")).when(fileStorage).delete(URL);
        TransactionSynchronizationManager.initSynchronization();

        cleaner.deleteOnRollback(URL);

        assertThatCode(() -> complete(TransactionSynchronization.STATUS_ROLLED_BACK))
                .doesNotThrowAnyException();
    }

    private void commit() {
        List<TransactionSynchronization> synchronizations =
                new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    }

    private void complete(int status) {
        List<TransactionSynchronization> synchronizations =
                new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
        synchronizations.forEach(s -> s.afterCompletion(status));
    }

    private void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
