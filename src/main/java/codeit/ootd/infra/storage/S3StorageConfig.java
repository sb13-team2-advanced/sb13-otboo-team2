package codeit.ootd.infra.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * S3 저장소 사용 시 지정한 리전과 기본 자격 증명 탐색 방식으로 클라이언트를 생성한다.
 * ECS에서는 태스크 역할에 부여된 임시 자격 증명을 사용한다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "otboo.storage.type", havingValue = "s3")
public class S3StorageConfig {

    @Bean(destroyMethod = "close")
    public S3Client s3Client(
            @Value("${otboo.storage.s3.region:ap-northeast-2}") String region
    ) {
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }
}