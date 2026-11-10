package codeit.ootd.infra.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 이미지 등 업로드 파일의 저장소 추상화. 로컬 저장소 구현을 사용하다가 S3 등으로 구현체만 교체할 수 있다.
 */
public interface FileStorage {

    /**
     * 파일을 지정한 디렉터리에 저장하고 접근 가능한 URL을 반환한다.
     *
     * @param file      업로드된 파일
     * @param directory 저장 디렉터리 (예: "clothes", "profiles")
     * @return 저장된 파일의 접근 URL
     */
    String upload(MultipartFile file, String directory);

    /**
     * 저장된 파일을 삭제한다. 대상이 없거나 이 저장소의 URL이 아니면 아무 작업도 하지 않는다.
     *
     * @param fileUrl {@link #upload}가 반환한 URL
     */
    void delete(String fileUrl);
}
