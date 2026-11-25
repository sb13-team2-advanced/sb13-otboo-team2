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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @ParameterizedTest
    @CsvSource({
            "shirt.jpg, image/jpeg, image/jpeg",
            "shirt.jpeg, image/jpeg, image/jpeg",
            "shirt.png, image/png, image/png",
            "shirt.gif, image/gif, image/gif",
            "shirt.webp, image/webp, image/webp",
            "shirt.PNG, IMAGE/PNG, image/png"
    })
    void 확장자와_MIME_타입이_일치하면_표준_콘텐츠_타입으로_업로드한다(
            String filename, String contentType, String expectedContentType
    ) {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "image", filename, contentType, new byte[]{1}
        );

        // when
        String url = storage.upload(file, "profiles");

        // then
        ArgumentCaptor<PutObjectRequest> request =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().contentType()).isEqualTo(expectedContentType);
        assertThat(request.getValue().key()).startsWith("images/profiles/");
        assertThat(url).isEqualTo(BASE_URL + "/" + request.getValue().key());
    }

    @ParameterizedTest
    @CsvSource({
            "shirt.png, image/svg+xml",
            "shirt.png, image/jpeg",
            "shirt.jpg, image/png",
            "shirt.jpeg, image/jpg",
            "shirt.gif, image/webp",
            "shirt.webp, image/gif",
            "shirt.svg, image/svg+xml",
            "shirt.png,"
    })
    void 확장자와_MIME_타입이_일치하지_않으면_S3에_전송하지_않는다(
            String filename, String contentType
    ) {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "image", filename, contentType, new byte[]{1}
        );

        // when/then
        assertThatThrownBy(() -> storage.upload(file, "clothes"))
                .isInstanceOfSatisfying(OtbooException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(ErrorCode.FILE_INVALID_TYPE));
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

    @ParameterizedTest
    @CsvSource({
            "image, application/octet-stream, PNG, png, image/png",
            "image.jpg?w=500, application/octet-stream, PNG, png, image/png",
            "photo.avif, application/octet-stream, JPEG, jpg, image/jpeg",
            "item, , GIF, gif, image/gif",
            "item.png, '', WEBP, webp, image/webp"
    })
    void 콘텐츠_타입이_없거나_octet_stream이면_파일_내용으로_형식을_판별해_업로드한다(
            String filename, String contentType, String kind,
            String expectedExtension, String expectedContentType
    ) {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "image", filename, contentType, imageBytes(kind)
        );

        // when
        String url = storage.upload(file, "clothes");

        // then
        ArgumentCaptor<PutObjectRequest> request =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().key())
                .startsWith("images/clothes/")
                .endsWith("." + expectedExtension);
        assertThat(request.getValue().contentType()).isEqualTo(expectedContentType);
        assertThat(url).isEqualTo(BASE_URL + "/" + request.getValue().key());
    }

    @ParameterizedTest
    @CsvSource({
            "note.txt, application/octet-stream, hello",
            "fake.png, application/octet-stream, not-an-image",
            "image, , '<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>'"
    })
    void 콘텐츠_타입이_없어도_내용이_이미지가_아니면_S3에_전송하지_않는다(
            String filename, String contentType, String content
    ) {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "image", filename, contentType, content.getBytes(StandardCharsets.UTF_8)
        );

        // when/then
        assertThatThrownBy(() -> storage.upload(file, "clothes"))
                .isInstanceOfSatisfying(OtbooException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(ErrorCode.FILE_INVALID_TYPE));
        verifyNoInteractions(s3Client);
    }

    @Test
    void 내용이_너무_짧아_형식을_판별할_수_없으면_S3에_전송하지_않는다() {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "image", "image", "application/octet-stream", new byte[]{1}
        );

        // when/then
        assertThatThrownBy(() -> storage.upload(file, "clothes"))
                .isInstanceOfSatisfying(OtbooException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(ErrorCode.FILE_INVALID_TYPE));
        verifyNoInteractions(s3Client);
    }

    @Test
    void 이미지가_아닌_타입을_명시했다면_내용이_이미지여도_거절한다() {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "image", "shirt.png", "text/plain", imageBytes("PNG")
        );

        // when/then
        assertThatThrownBy(() -> storage.upload(file, "clothes"))
                .isInstanceOfSatisfying(OtbooException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(ErrorCode.FILE_INVALID_TYPE));
        verifyNoInteractions(s3Client);
    }

    /**
     * 각 이미지 형식의 시그니처(매직 바이트)로 시작하는 최소 바이트열을 만든다.
     */
    private byte[] imageBytes(String kind) {
        return switch (kind) {
            case "PNG" -> new byte[]{
                    (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
            };
            case "JPEG" -> new byte[]{
                    (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1
            };
            case "GIF" -> "GIF89a\u0001\u0000\u0001\u0000".getBytes(StandardCharsets.ISO_8859_1);
            case "WEBP" -> new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
            default -> throw new IllegalArgumentException(kind);
        };
    }
}
