package codeit.ootd.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

public final class SpaCsrfTokenRequestHandler
        implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestAttributeHandler plain =
            new CsrfTokenRequestAttributeHandler();

    private final XorCsrfTokenRequestAttributeHandler xor =
            new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            Supplier<CsrfToken> csrfToken
    ) {
        // 응답에서 노출하는 토큰은 XOR 방식으로 처리
        xor.handle(request, response, csrfToken);

        // 지연 생성된 CSRF 토큰을 실제로 로드
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(
            HttpServletRequest request,
            CsrfToken csrfToken
    ) {
        // SPA가 쿠키의 원본 토큰을 헤더로 전달한 경우
        if (StringUtils.hasText(
                request.getHeader(csrfToken.getHeaderName())
        )) {
            return plain.resolveCsrfTokenValue(request, csrfToken);
        }

        // 그 외에는 기존 XOR 방식 유지
        return xor.resolveCsrfTokenValue(request, csrfToken);
    }
}