package codeit.ootd.infra.storage;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * 검증한 이미지를 S3의 images 경로에 UUID 파일명으로 저장하고
 * CloudFront URL을 반환한다. 삭제는 이 저장소에서 생성한 URL에 한정한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "otboo.storage.type", havingValue = "s3")
public class S3FileStorage implements FileStorage {

    private static final String KEY_PREFIX = "images/";
    private static final Set<String> ALLOWED_DIRECTORIES = Set.of("clothes", "profiles");
    private static final Pattern OBJECT_KEY_PATTERN = Pattern.compile(
            "images/(clothes|profiles)/[0-9a-f-]{36}\\.(jpg|jpeg|png|gif|webp)"
    );

    private final S3Client s3Client;
    private final String bucket;
    private final String baseUrl;

    public S3FileStorage(
            S3Client s3Client,
            @Value("${otboo.storage.s3.bucket}") String bucket,
            @Value("${otboo.storage.s3.base-url}") String baseUrl
    ) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    /**
     * 이미지 형식과 디렉터리를 검증한 뒤 S3에 저장하고 접근 URL을 반환한다.
     * 파일 읽기 또는 AWS 요청 실패는 기존 업로드 실패 예외로 변환한다.
     */
    @Override
    public String upload(MultipartFile file, String directory) {
        ImageTypeResolver.ImageType imageType = ImageTypeResolver.resolve(file);

        if (directory == null || !ALLOWED_DIRECTORIES.contains(directory)) {
            throw new IllegalArgumentException(
                    "지원하지 않는 저장 디렉터리입니다: " + directory
            );
        }

        String objectKey = KEY_PREFIX + directory + "/" + UUID.randomUUID()
                + "." + imageType.extension();

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(imageType.contentType())
                .build();

        try {
            s3Client.putObject(
                    request,
                    RequestBody.fromBytes(file.getBytes())
            );
        } catch (IOException | SdkException e) {
            log.error(
                    "S3 파일 업로드 실패: bucket={}, key={}",
                    bucket, objectKey, e
            );
            throw new OtbooException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        return baseUrl + "/" + objectKey;
    }

    /**
     * 자신의 기본 URL과 UUID 객체 키에 해당하는 파일만 삭제한다.
     * 삭제 실패는 로그로 남겨 이미 처리한 요청의 결과를 유지한다.
     */
    @Override
    public void delete(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }

        String urlPrefix = baseUrl + "/";
        if (!fileUrl.startsWith(urlPrefix)) {
            return;
        }

        String objectKey = fileUrl.substring(urlPrefix.length());
        if (!OBJECT_KEY_PATTERN.matcher(objectKey).matches()) {
            return;
        }

        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        try {
            s3Client.deleteObject(request);
        } catch (SdkException e) {
            log.warn(
                    "S3 파일 삭제 실패: bucket={}, key={}",
                    bucket, objectKey, e
            );
        }
    }
}
