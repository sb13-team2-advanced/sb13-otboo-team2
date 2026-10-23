package codeit.ootd.infra.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 로컬에 저장된 파일을 {@code baseUrl} 경로(기본 /files/**)로 내려주도록 정적 리소스 핸들러를 등록한다.
 */
@Configuration
@ConditionalOnProperty(name = "otboo.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageWebConfig implements WebMvcConfigurer {

    private final LocalFileStorage localFileStorage;

    public LocalStorageWebConfig(LocalFileStorage localFileStorage) {
        this.localFileStorage = localFileStorage;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = localFileStorage.getRootPath().toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler(localFileStorage.getBaseUrl() + "/**")
            .addResourceLocations(location);
    }
}
