package codeit.ootd.infra.web;

public interface WebPageFetcher {

    /**
     * 주소의 HTML 문서를 문자열로 가져온다.
     */
    String fetch(String url);
}
