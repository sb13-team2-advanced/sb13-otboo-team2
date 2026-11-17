package codeit.ootd.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI(
            @Value("${ootd.openapi.server-url:/}") String serverUrl,
            @Value("${ootd.openapi.server-description:현재 서버}")
            String serverDescription
    ) {
        return new OpenAPI()
                .info(new Info()
                        .title("옷장을 부탁해 API 문서")
                        .description("옷장을 부탁해 프로젝트의 Swagger API 문서입니다.")
                        .version("1.0"))
                .addServersItem(new Server()
                        .url(serverUrl)
                        .description(serverDescription))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("로그인 후 발급받은 JWT 액세스 토큰"))
                        .addSecuritySchemes("CsrfToken",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-XSRF-TOKEN")
                                        .description("CSRF 토큰: /api/auth/csrf-token에서 발급")));
    }
}