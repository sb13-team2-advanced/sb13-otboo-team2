package codeit.ootd.infra.web;

public interface WebPageFetcher {

    /**
     * 주소의 HTML 문서를 가져온다. 리다이렉트를 따라갔다면 최종 주소를 함께 돌려준다.
     */
    FetchedPage fetch(String url);
}
