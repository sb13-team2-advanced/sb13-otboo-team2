package codeit.ootd.domain.clothes.service.impl;

import codeit.ootd.domain.clothes.dto.response.ClothesAttributeWithDefDto;
import codeit.ootd.domain.clothes.dto.response.ClothesDto;
import codeit.ootd.domain.clothes.entity.ClothesAttributeDefinition;
import codeit.ootd.domain.clothes.extraction.ClothesPageParser;
import codeit.ootd.domain.clothes.extraction.ClothesTypeResolver;
import codeit.ootd.domain.clothes.extraction.ParsedPage;
import codeit.ootd.domain.clothes.repository.ClothesAttributeDefinitionRepository;
import codeit.ootd.domain.clothes.service.ClothesExtractionService;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import codeit.ootd.infra.web.WebPageFetcher;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 외부 페이지를 기다리는 동안 DB 연결을 잡고 있지 않도록 트랜잭션을 걸지 않고, 페이지를 먼저 가져온 뒤 DB를 읽는다.
 */
@Service
@RequiredArgsConstructor
public class ClothesExtractionServiceImpl implements ClothesExtractionService {

    private static final int MAX_NAME_LENGTH = 50;

    private final WebPageFetcher pageFetcher;
    private final ClothesPageParser pageParser;
    private final ClothesTypeResolver typeResolver;
    private final ClothesAttributeDefinitionRepository definitionRepository;

    @Override
    public ClothesDto extract(String url) {
        String html = pageFetcher.fetch(url);
        ParsedPage page = pageParser.parse(html, url);
        if (page.title() == null) {
            throw new OtbooException(ErrorCode.CLOTHES_EXTRACTION_NOT_FOUND);
        }

        String title = page.title();
        String name = title.length() > MAX_NAME_LENGTH ? title.substring(0, MAX_NAME_LENGTH) : title;
        return new ClothesDto(null, null, name, page.imageUrl(), typeResolver.resolve(title), matchAttributes(title));
    }

    /**
     * 제목에 선택값이 들어 있는 속성 정의만 골라 값을 채운다.
     */
    private List<ClothesAttributeWithDefDto> matchAttributes(String title) {
        String lowered = title.toLowerCase(Locale.ROOT);
        return definitionRepository.findAll().stream()
                .map(definition -> toAttribute(definition, lowered))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private ClothesAttributeWithDefDto toAttribute(ClothesAttributeDefinition definition, String loweredTitle) {
        return definition.getSelectableValues().stream()
                .filter(value -> !value.isBlank() && loweredTitle.contains(value.toLowerCase(Locale.ROOT)))
                .findFirst()
                .map(value -> new ClothesAttributeWithDefDto(definition.getId(), definition.getName(),
                        definition.getSelectableValues(), value))
                .orElse(null);
    }
}
