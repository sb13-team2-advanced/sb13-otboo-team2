package codeit.ootd.domain.clothes.service.impl;

import codeit.ootd.domain.clothes.dto.response.ClothesDto;
import codeit.ootd.domain.clothes.extraction.ClothesPageParser;
import codeit.ootd.domain.clothes.extraction.ClothesTypeResolver;
import codeit.ootd.domain.clothes.repository.ClothesAttributeDefinitionRepository;
import codeit.ootd.domain.clothes.service.ClothesExtractionService;
import codeit.ootd.infra.web.WebPageFetcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClothesExtractionServiceImpl implements ClothesExtractionService {

    private final WebPageFetcher pageFetcher;
    private final ClothesPageParser pageParser;
    private final ClothesTypeResolver typeResolver;
    private final ClothesAttributeDefinitionRepository definitionRepository;

    @Override
    public ClothesDto extract(String url) {
        throw new UnsupportedOperationException("not implemented");
    }
}
