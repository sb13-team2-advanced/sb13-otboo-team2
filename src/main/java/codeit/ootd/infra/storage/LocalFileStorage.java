package codeit.ootd.infra.storage;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 서버 로컬 디스크에 이미지를 저장하는 {@link FileStorage} 구현체.
 * 저장 파일명은 UUID로 새로 만들어 원본 파일명으로 인한 경로 조작과 중복을 막는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "otboo.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    @Getter
    private final Path rootPath;

    @Getter
    private final String baseUrl;

    public LocalFileStorage(
        @Value("${otboo.storage.local.root-path:./uploads}") String rootPath,
        @Value("${otboo.storage.local.base-url:/files}") String baseUrl
    ) {
        this.rootPath = Path.of(rootPath).toAbsolutePath().normalize();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public String upload(MultipartFile file, String directory) {
        validate(file);

        Path targetDirectory = rootPath.resolve(directory).normalize();
        if (!targetDirectory.startsWith(rootPath)) {
            throw new IllegalArgumentException("저장소 밖의 경로에는 저장할 수 없습니다: " + directory);
        }

        String extension = extractExtension(file.getOriginalFilename());
        Path target = targetDirectory.resolve(UUID.randomUUID() + "." + extension);

        try (InputStream inputStream = file.getInputStream()) {
            Files.createDirectories(targetDirectory);
            Files.copy(inputStream, target);
        } catch (IOException e) {
            log.error("파일 저장 실패: target={}", target, e);
            throw new OtbooException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        String relativePath = rootPath.relativize(target).toString().replace('\\', '/');
        return baseUrl + "/" + relativePath;
    }

    @Override
    public void delete(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }

        String prefix = baseUrl + "/";
        if (!fileUrl.startsWith(prefix)) {
            return;
        }

        Path target = rootPath.resolve(fileUrl.substring(prefix.length())).normalize();
        if (!target.startsWith(rootPath)) {
            log.warn("저장소 밖의 경로는 삭제하지 않습니다: url={}", fileUrl);
            return;
        }

        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("파일 삭제 실패: target={}", target, e);
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new OtbooException(ErrorCode.FILE_EMPTY);
        }

        String contentType = file.getContentType();
        String extension = extractExtension(file.getOriginalFilename());
        if (contentType == null || !contentType.startsWith("image/") || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new OtbooException(ErrorCode.FILE_INVALID_TYPE);
        }
    }

    private String extractExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
