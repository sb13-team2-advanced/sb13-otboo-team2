package codeit.ootd;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * main 메서드를 통한 애플리케이션 시작과 테스트 환경의 컨텍스트 구성을 검증한다.
 */
@ActiveProfiles("test")
@SpringBootTest(useMainMethod = SpringBootTest.UseMainMethod.ALWAYS)
class Sb13OtbooTeam2ApplicationTests {

    @Test
    void 메인_메서드로_애플리케이션_컨텍스트가_시작된다() {
        // given/when: SpringBootTest가 main 메서드로 컨텍스트를 시작한다.
        // then: 컨텍스트 시작 실패 시 테스트가 실패한다.
    }

}
