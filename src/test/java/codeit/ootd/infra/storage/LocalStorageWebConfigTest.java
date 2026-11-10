package codeit.ootd.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

/**
 * 로컬 저장소 URL 경로가 정적 리소스 핸들러로 등록되는지 검증한다.
 */
class LocalStorageWebConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void 저장소_기본_URL_경로에_정적_리소스_핸들러가_등록된다() {
        // given
        LocalFileStorage storage = new LocalFileStorage(tempDir.resolve("uploads").toString(), "/files");
        LocalStorageWebConfig config = new LocalStorageWebConfig(storage);
        ResourceHandlerRegistry registry =
            new ResourceHandlerRegistry(new GenericWebApplicationContext(), new MockServletContext());

        // when
        config.addResourceHandlers(registry);

        // then
        assertThat(registry.hasMappingForPattern("/files/**")).isTrue();
    }
}
