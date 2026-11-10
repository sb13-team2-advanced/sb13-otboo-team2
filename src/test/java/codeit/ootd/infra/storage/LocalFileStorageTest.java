package codeit.ootd.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * 로컬 파일 저장소의 이미지 저장·삭제와 검증 실패, 저장소 밖 경로 차단을 검증한다.
 */
class LocalFileStorageTest {

    private static final String BASE_URL = "/files";

    @TempDir
    Path tempDir;

    private Path root;
    private LocalFileStorage storage;

    @BeforeEach
    void setUp() {
        root = tempDir.resolve("root");
        storage = new LocalFileStorage(root.toString(), BASE_URL + "/");
    }

    @Test
    void 이미지를_저장하면_URL을_반환하고_파일이_생성된다() throws IOException {
        // given
        MockMultipartFile file = image("shirt.PNG", "image/png", "image-bytes");

        // when
        String url = storage.upload(file, "clothes");

        // then
        assertThat(url).startsWith("/files/clothes/").endsWith(".png");
        Path saved = root.resolve(url.substring("/files/".length()));
        assertThat(saved).exists();
        assertThat(Files.readString(saved, StandardCharsets.UTF_8)).isEqualTo("image-bytes");
    }

    @Test
    void 같은_이름의_파일을_저장해도_URL이_겹치지_않는다() {
        // given
        MockMultipartFile first = image("shirt.jpg", "image/jpeg", "a");
        MockMultipartFile second = image("shirt.jpg", "image/jpeg", "b");

        // when
        String firstUrl = storage.upload(first, "clothes");
        String secondUrl = storage.upload(second, "clothes");

        // then
        assertThat(firstUrl).isNotEqualTo(secondUrl);
    }

    @Test
    void 빈_파일이면_예외가_발생한다() {
        // given
        MockMultipartFile empty = new MockMultipartFile("image", "empty.png", "image/png", new byte[0]);

        // when/then
        assertThatThrownBy(() -> storage.upload(empty, "clothes"))
            .isInstanceOfSatisfying(OtbooException.class,
                e -> assertThat(e.getErrorCode()).isSameAs(ErrorCode.FILE_EMPTY));
    }

    @Test
    void 파일이_null이면_예외가_발생한다() {
        // when/then
        assertThatThrownBy(() -> storage.upload(null, "clothes"))
            .isInstanceOfSatisfying(OtbooException.class,
                e -> assertThat(e.getErrorCode()).isSameAs(ErrorCode.FILE_EMPTY));
    }

    @Test
    void 허용하지_않는_확장자면_예외가_발생한다() {
        // given
        MockMultipartFile file = image("note.txt", "image/png", "text");

        // when/then
        assertThatThrownBy(() -> storage.upload(file, "clothes"))
            .isInstanceOfSatisfying(OtbooException.class,
                e -> assertThat(e.getErrorCode()).isSameAs(ErrorCode.FILE_INVALID_TYPE));
    }

    @Test
    void 이미지가_아닌_콘텐츠_타입이면_예외가_발생한다() {
        // given
        MockMultipartFile file = image("fake.png", "text/plain", "text");

        // when/then
        assertThatThrownBy(() -> storage.upload(file, "clothes"))
            .isInstanceOfSatisfying(OtbooException.class,
                e -> assertThat(e.getErrorCode()).isSameAs(ErrorCode.FILE_INVALID_TYPE));
    }

    @Test
    void 콘텐츠_타입이_없으면_예외가_발생한다() {
        // given
        MockMultipartFile file = new MockMultipartFile("image", "shirt.png", null, "a".getBytes());

        // when/then
        assertThatThrownBy(() -> storage.upload(file, "clothes"))
            .isInstanceOfSatisfying(OtbooException.class,
                e -> assertThat(e.getErrorCode()).isSameAs(ErrorCode.FILE_INVALID_TYPE));
    }

    @Test
    void 확장자가_없거나_파일명이_비어_있으면_예외가_발생한다() {
        // given
        MockMultipartFile noExtension = image("image", "image/png", "a");
        MockMultipartFile endsWithDot = image("image.", "image/png", "a");
        MockMultipartFile noName = new MockMultipartFile("image", null, "image/png", "a".getBytes());

        // when/then
        for (MultipartFile file : new MultipartFile[]{noExtension, endsWithDot, noName}) {
            assertThatThrownBy(() -> storage.upload(file, "clothes"))
                .isInstanceOfSatisfying(OtbooException.class,
                    e -> assertThat(e.getErrorCode()).isSameAs(ErrorCode.FILE_INVALID_TYPE));
        }
    }

    @Test
    void 저장소_밖으로_벗어나는_디렉터리면_예외가_발생한다() {
        // given
        MockMultipartFile file = image("shirt.png", "image/png", "a");

        // when/then
        assertThatThrownBy(() -> storage.upload(file, "../outside"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(tempDir.resolve("outside")).doesNotExist();
    }

    @Test
    void 파일을_읽는_중_오류가_나면_저장_실패_예외가_발생한다() {
        // given
        MockMultipartFile broken = new MockMultipartFile("image", "shirt.png", "image/png", "a".getBytes()) {
            @Override
            public InputStream getInputStream() throws IOException {
                throw new IOException("read failed");
            }
        };

        // when/then
        assertThatThrownBy(() -> storage.upload(broken, "clothes"))
            .isInstanceOfSatisfying(OtbooException.class,
                e -> assertThat(e.getErrorCode()).isSameAs(ErrorCode.FILE_UPLOAD_FAILED));
    }

    @Test
    void 일부만_저장된_뒤_실패하면_저장된_파일을_정리한다() {
        // given: 5바이트를 읽은 뒤 읽기 오류가 발생해 일부만 기록되는 파일
        MockMultipartFile partial = new MockMultipartFile("image", "shirt.png", "image/png", "a".getBytes()) {
            @Override
            public InputStream getInputStream() {
                return new InputStream() {
                    private int count = 0;

                    @Override
                    public int read() throws IOException {
                        if (count++ < 5) {
                            return 'a';
                        }
                        throw new IOException("read failed");
                    }
                };
            }
        };

        // when/then
        assertThatThrownBy(() -> storage.upload(partial, "clothes"))
            .isInstanceOfSatisfying(OtbooException.class,
                e -> assertThat(e.getErrorCode()).isSameAs(ErrorCode.FILE_UPLOAD_FAILED));
        assertThat(root.resolve("clothes")).isEmptyDirectory();
    }

    @Test
    void 저장한_파일을_삭제한다() {
        // given
        String url = storage.upload(image("shirt.png", "image/png", "a"), "clothes");
        Path saved = root.resolve(url.substring("/files/".length()));

        // when
        storage.delete(url);

        // then
        assertThat(saved).doesNotExist();
    }

    @Test
    void 존재하지_않는_파일이나_빈_값_삭제는_예외없이_넘어간다() {
        // when/then
        assertThatCode(() -> {
            storage.delete("/files/clothes/none.png");
            storage.delete(null);
            storage.delete("  ");
        }).doesNotThrowAnyException();
    }

    @Test
    void 저장소_URL이_아니면_삭제하지_않는다() throws IOException {
        // given
        Path other = Files.createDirectories(root).resolve("keep.png");
        Files.writeString(other, "keep");

        // when
        storage.delete("https://example.com/keep.png");

        // then
        assertThat(other).exists();
    }

    @Test
    void 저장소_밖의_경로는_삭제하지_않는다() throws IOException {
        // given
        Files.createDirectories(root);
        Path outside = tempDir.resolve("outside.png");
        Files.writeString(outside, "keep");

        // when
        storage.delete("/files/../outside.png");

        // then
        assertThat(outside).exists();
    }

    @Test
    void 삭제에_실패해도_예외를_던지지_않는다() {
        // given: 비어 있지 않은 디렉터리는 삭제할 수 없다
        storage.upload(image("shirt.png", "image/png", "a"), "clothes");

        // when/then
        assertThatCode(() -> storage.delete("/files/clothes")).doesNotThrowAnyException();
        assertThat(root.resolve("clothes")).exists();
    }

    private MockMultipartFile image(String filename, String contentType, String content) {
        return new MockMultipartFile("image", filename, contentType, content.getBytes(StandardCharsets.UTF_8));
    }
}
