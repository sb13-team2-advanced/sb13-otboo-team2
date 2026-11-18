package codeit.ootd.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * S3 업로드 내용, 반환 URL, 삭제 대상 제한과 AWS 요청 실패 처리를 검증한다.
 */
class S3FileStorageTest {

    private static final String BUCKET = "test-images";
    private static final String BASE_URL = "https://images.example.com";
    private static final String OBJECT_KEY =
            "images/clothes/123e4567-e89b-12d3-a456-426614174000.png";

    private S3Client s3Client;
    private S3FileStorage storage;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        storage = new S3FileStorage(s3Client, BUCKET, BASE_URL + "/");
    }

    @Test
    void 이미지를_업로드하면_파일_내용을_전송하고_URL을_반환한다()
            throws IOException {
        // given
        byte[] content = "image-content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file =
                new MockMultipartFile("image", "shirt.PNG", "image/png", content);

        // when
        String url = storage.upload(file, "clothes");

        // then
        ArgumentCaptor<PutObjectRequest> request =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> body =
                ArgumentCaptor.forClass(RequestBody.class);

        verify(s3Client).putObject(request.capture(), body.capture());

        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().key())
                .startsWith("images/clothes/")
                .endsWith(".png");
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
        assertThat(url).isEqualTo(BASE_URL + "/" + request.getValue().key());

        try (var stream = body.getValue().contentStreamProvider().newStream()) {
            assertThat(stream.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void 빈_파일은_S3에_전송하지_않는다() {
        // given
        MockMultipartFile file =
                new MockMultipartFile("image", "empty.png", "image/png", new byte[0]);

        // when/then
        assertThatThrownBy(() -> storage.upload(file, "clothes"))
                .isInstanceOfSatisfying(OtbooException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FILE_EMPTY));

        verifyNoInteractions(s3Client);
    }

    @Test
    void 이미지가_아닌_파일은_S3에_전송하지_않는다() {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "image", "note.txt", "text/plain", new byte[]{1}
        );

        // when/then
        assertThatThrownBy(() -> storage.upload(file, "clothes"))
                .isInstanceOfSatisfying(OtbooException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(ErrorCode.FILE_INVALID_TYPE));

        verifyNoInteractions(s3Client);
    }

    @Test
    void 허용하지_않는_디렉터리에는_업로드하지_않는다() {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "image", "shirt.png", "image/png", new byte[]{1}
        );

        // when/then
        assertThatThrownBy(() -> storage.upload(file, "../outside"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(s3Client);
    }

    @Test
    void S3_업로드_실패는_파일_저장_실패로_처리한다() {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "image", "shirt.png", "image/png", new byte[]{1}
        );
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().statusCode(403).build());

        // when/then
        assertThatThrownBy(() -> storage.upload(file, "clothes"))
                .isInstanceOfSatisfying(OtbooException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(ErrorCode.FILE_UPLOAD_FAILED));
    }

    @Test
    void 자신의_이미지_URL에_해당하는_객체를_삭제한다() {
        // given
        String url = BASE_URL + "/" + OBJECT_KEY;

        // when
        storage.delete(url);

        // then
        ArgumentCaptor<DeleteObjectRequest> request =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);

        verify(s3Client).deleteObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().key()).isEqualTo(OBJECT_KEY);
    }

    @Test
    void 외부_URL이나_잘못된_경로는_삭제하지_않는다() {
        // given
        String externalUrl = "https://other.example.com/" + OBJECT_KEY;
        String invalidUrl = BASE_URL + "/images/clothes/../other.png";

        // when
        storage.delete(externalUrl);
        storage.delete(invalidUrl);
        storage.delete(null);
        storage.delete("");

        // then
        verifyNoInteractions(s3Client);
    }

    @Test
    void S3_삭제에_실패해도_예외를_던지지_않는다() {
        // given
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(503).build());

        // when/then
        assertThatCode(() -> storage.delete(BASE_URL + "/" + OBJECT_KEY))
                .doesNotThrowAnyException();
    }
}