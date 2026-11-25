package codeit.ootd.infra.storage;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드된 이미지의 형식을 검증하고 저장에 쓸 확장자와 콘텐츠 타입을 정한다.
 * 클라이언트가 이미지 타입을 명시하면 확장자와 일치하는지 엄격하게 검사하고,
 * 타입이 없거나 application/octet-stream이면 파일 앞부분의 시그니처로 형식을 판별한다.
 */
final class ImageTypeResolver {

    private static final String OCTET_STREAM = "application/octet-stream";
    private static final int HEADER_LENGTH = 12;

    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
        "jpg", "image/jpeg",
        "jpeg", "image/jpeg",
        "png", "image/png",
        "gif", "image/gif",
        "webp", "image/webp"
    );

    private ImageTypeResolver() {
    }

    /**
     * 저장할 이미지의 확장자와 콘텐츠 타입.
     */
    record ImageType(String extension, String contentType) {
    }

    /**
     * 빈 파일을 차단하고, 허용한 이미지 형식인지 확인해 저장 형식을 반환한다.
     */
    static ImageType resolve(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new OtbooException(ErrorCode.FILE_EMPTY);
        }

        String declaredType = file.getContentType();
        if (declaredType == null || declaredType.isBlank()
            || OCTET_STREAM.equalsIgnoreCase(declaredType.trim())) {
            return detectBySignature(file);
        }

        String extension = extractExtension(file.getOriginalFilename());
        String expectedContentType = ALLOWED_CONTENT_TYPES.get(extension);
        if (expectedContentType == null || !expectedContentType.equalsIgnoreCase(declaredType)) {
            throw new OtbooException(ErrorCode.FILE_INVALID_TYPE);
        }
        return new ImageType(extension, expectedContentType);
    }

    /**
     * 파일명과 클라이언트 타입을 믿지 않고 파일 앞부분 바이트로 형식을 판별한다.
     */
    private static ImageType detectBySignature(MultipartFile file) {
        byte[] header;
        try (InputStream inputStream = file.getInputStream()) {
            header = inputStream.readNBytes(HEADER_LENGTH);
        } catch (IOException e) {
            throw new OtbooException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        if (startsWith(header, 0, (byte) 0xFF, (byte) 0xD8, (byte) 0xFF)) {
            return new ImageType("jpg", "image/jpeg");
        }
        if (startsWith(header, 0, (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return new ImageType("png", "image/png");
        }
        if (startsWithAscii(header, 0, "GIF87a") || startsWithAscii(header, 0, "GIF89a")) {
            return new ImageType("gif", "image/gif");
        }
        if (startsWithAscii(header, 0, "RIFF") && startsWithAscii(header, 8, "WEBP")) {
            return new ImageType("webp", "image/webp");
        }
        throw new OtbooException(ErrorCode.FILE_INVALID_TYPE);
    }

    private static boolean startsWithAscii(byte[] header, int offset, String text) {
        return startsWith(header, offset, text.getBytes(StandardCharsets.US_ASCII));
    }

    private static boolean startsWith(byte[] header, int offset, int... expected) {
        byte[] bytes = new byte[expected.length];
        for (int i = 0; i < expected.length; i++) {
            bytes[i] = (byte) expected[i];
        }
        return startsWith(header, offset, bytes);
    }

    private static boolean startsWith(byte[] header, int offset, byte[] expected) {
        if (header.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (header[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static String extractExtension(String filename) {
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
