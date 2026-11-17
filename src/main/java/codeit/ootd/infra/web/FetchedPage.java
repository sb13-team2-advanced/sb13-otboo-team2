package codeit.ootd.infra.web;

/**
 * 가져온 HTML과, 리다이렉트를 모두 따라간 뒤의 최종 주소. 상대 경로 해석은 최종 주소를 기준으로 해야 한다.
 */
public record FetchedPage(String html, String url) {
}
