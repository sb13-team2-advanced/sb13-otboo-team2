package codeit.ootd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 자동 설정과 컴포넌트 스캔으로 애플리케이션을 구성한다.
 */
@SpringBootApplication
public class Sb13OtbooTeam2Application {

    /**
     * 실행 인자를 전달해 Spring 애플리케이션 컨텍스트를 시작한다.
     */
    public static void main(String[] args) {
        SpringApplication.run(Sb13OtbooTeam2Application.class, args);
    }

}
