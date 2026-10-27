package codeit.ootd.global.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Spring 캐시 기능을 활성화한다.
 * 캐시별 TTL과 직렬화 정책은 실제 적용 시 추가한다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

}
