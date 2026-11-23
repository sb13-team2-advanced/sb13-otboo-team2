package codeit.ootd.domain.clothes.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 의상 관련 API가 코드잇 Swagger 명세와 같은 태그, 요약, operationId, 응답 코드로 문서화되는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("의상 API 문서 테스트")
class ClothesApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    private String apiDocs;

    @BeforeEach
    void loadApiDocs() throws Exception {
        // Swagger 문서 허용은 dev 프로필에만 있어서, 테스트에서는 인증된 요청으로 문서를 가져온다.
        apiDocs = mockMvc.perform(get("/v3/api-docs").with(user("api-docs")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @ParameterizedTest(name = "{0} {1}")
    @DisplayName("의상 API는 명세의 태그, 요약, operationId, 응답 코드로 문서화된다")
    @CsvSource(delimiter = '|', value = {
            "get|/api/clothes|getClothes|옷 목록 조회|의상 관리|200,400",
            "post|/api/clothes|createClothes|옷 등록|의상 관리|201,400",
            "patch|/api/clothes/{clothesId}|updateClothes|옷 수정|의상 관리|200,400",
            "delete|/api/clothes/{clothesId}|deleteClothes|옷 삭제|의상 관리|204,400",
            "get|/api/clothes/extractions|extractByUrl|구매 링크로 옷 정보 불러오기|의상 관리|200,400,429",
            "get|/api/clothes/attribute-defs|getClothesAttributeDef|의상 속성 정의 목록 조회|의상 속성 정의|200,400",
            "post|/api/clothes/attribute-defs|createClothesAttributeDef|의상 속성 정의 등록|의상 속성 정의|201,400",
            "patch|/api/clothes/attribute-defs/{definitionId}|updateClothesAttributeDef|의상 속성 정의 수정|의상 속성 정의|200,400",
            "delete|/api/clothes/attribute-defs/{definitionId}|deleteClothesAttributeDef|의상 속성 정의 삭제|의상 속성 정의|204,400"
    })
    void documentsOperationLikeSpec(
            String method,
            String path,
            String operationId,
            String summary,
            String tag,
            String statuses
    ) {
        String operation = "$.paths['" + path + "']." + method;

        assertThat(JsonPath.<String>read(apiDocs, operation + ".operationId")).isEqualTo(operationId);
        assertThat(JsonPath.<String>read(apiDocs, operation + ".summary")).isEqualTo(summary);
        assertThat(JsonPath.<String>read(apiDocs, operation + ".tags[0]")).isEqualTo(tag);

        Map<String, Object> responses = JsonPath.read(apiDocs, operation + ".responses");
        assertThat(responses.keySet()).containsAll(Arrays.asList(statuses.split(",")));
    }
}
