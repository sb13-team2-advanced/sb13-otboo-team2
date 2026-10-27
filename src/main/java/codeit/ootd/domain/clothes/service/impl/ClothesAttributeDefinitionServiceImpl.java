package codeit.ootd.domain.clothes.service.impl;

import codeit.ootd.domain.clothes.dto.request.ClothesAttributeDefCreateRequest;
import codeit.ootd.domain.clothes.dto.request.ClothesAttributeDefUpdateRequest;
import codeit.ootd.domain.clothes.dto.response.ClothesAttributeDefDto;
import codeit.ootd.domain.clothes.dto.response.ClothesAttributeDefDtoCursorResponse;
import codeit.ootd.domain.clothes.entity.ClothesAttributeDefinition;
import codeit.ootd.domain.clothes.enums.ClothesAttributeDefSortBy;
import codeit.ootd.domain.clothes.exception.ClothesAttributeDefinitionNameDuplicatedException;
import codeit.ootd.domain.clothes.exception.ClothesAttributeDefinitionNotFoundException;
import codeit.ootd.domain.clothes.exception.ClothesAttributeValuesDuplicatedException;
import codeit.ootd.domain.clothes.exception.InvalidCursorException;
import codeit.ootd.domain.clothes.repository.ClothesAttributeDefinitionRepository;
import codeit.ootd.domain.clothes.repository.ClothesAttributeRepository;
import codeit.ootd.domain.clothes.service.ClothesAttributeDefinitionService;
import codeit.ootd.global.enums.SortDirection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClothesAttributeDefinitionServiceImpl implements ClothesAttributeDefinitionService {

    private final ClothesAttributeDefinitionRepository definitionRepository;
    private final ClothesAttributeRepository attributeRepository;

    @Override
    @Transactional
    public ClothesAttributeDefDto create(ClothesAttributeDefCreateRequest request) {
        String name = request.name().strip();
        List<String> values = normalizeValues(request.selectableValues());

        if (definitionRepository.existsByName(name)) {
            throw new ClothesAttributeDefinitionNameDuplicatedException();
        }

        ClothesAttributeDefinition saved = saveAndFlushUnique(new ClothesAttributeDefinition(name, values));
        log.info("의상 속성 정의 등록 완료: definitionId={}", saved.getId());
        return ClothesAttributeDefDto.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ClothesAttributeDefDtoCursorResponse list(
            String cursor,
            UUID idAfter,
            int limit,
            String sortBy,
            SortDirection sortDirection,
            String keywordLike) {

        ClothesAttributeDefSortBy sort = ClothesAttributeDefSortBy.from(sortBy);
        SortDirection direction = sortDirection == null ? SortDirection.DESCENDING : sortDirection;
        String keyword = normalizeKeyword(keywordLike);

        boolean hasCursor = cursor != null && !cursor.isBlank();
        if (hasCursor != (idAfter != null)) {
            throw new InvalidCursorException();
        }
        Object cursorValue = hasCursor ? sort.parseCursor(cursor) : null;

        List<ClothesAttributeDefinition> rows =
                definitionRepository.findPage(keyword, sort, direction, cursorValue, idAfter, limit + 1);

        boolean hasNext = rows.size() > limit;
        List<ClothesAttributeDefinition> page = hasNext ? rows.subList(0, limit) : rows;
        ClothesAttributeDefinition last = page.isEmpty() ? null : page.get(page.size() - 1);

        return new ClothesAttributeDefDtoCursorResponse(
                page.stream().map(ClothesAttributeDefDto::from).toList(),
                hasNext ? sort.cursorOf(last) : null,
                hasNext ? last.getId() : null,
                hasNext,
                definitionRepository.countByKeyword(keyword),
                sort.getProperty(),
                direction
        );
    }

    @Override
    @Transactional
    public ClothesAttributeDefDto update(UUID definitionId, ClothesAttributeDefUpdateRequest request) {
        ClothesAttributeDefinition definition = findOrThrow(definitionId);

        String name = request.name() == null ? definition.getName() : request.name().strip();
        List<String> values = request.selectableValues() == null
                ? new ArrayList<>(definition.getSelectableValues())
                : normalizeValues(request.selectableValues());

        if (!name.equals(definition.getName())
                && definitionRepository.existsByNameAndIdNot(name, definitionId)) {
            throw new ClothesAttributeDefinitionNameDuplicatedException();
        }

        definition.update(name, values);
        flushUnique();
        log.info("의상 속성 정의 수정 완료: definitionId={}", definitionId);
        return ClothesAttributeDefDto.from(definition);
    }

    /**
     * 정의를 지우면 의상에 붙은 해당 속성 값도 함께 지운다.
     */
    @Override
    @Transactional
    public void delete(UUID definitionId) {
        ClothesAttributeDefinition definition = findOrThrow(definitionId);

        attributeRepository.deleteAllByDefinitionId(definitionId);
        definitionRepository.delete(definition);
        log.info("의상 속성 정의 삭제 완료: definitionId={}", definitionId);
    }

    private ClothesAttributeDefinition findOrThrow(UUID definitionId) {
        return definitionRepository.findById(definitionId)
                .orElseThrow(ClothesAttributeDefinitionNotFoundException::new);
    }

    private List<String> normalizeValues(List<String> values) {
        List<String> normalized = values.stream().map(String::strip).toList();
        if (new HashSet<>(normalized).size() != normalized.size()) {
            throw new ClothesAttributeValuesDuplicatedException();
        }
        return normalized;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.strip();
    }

    /** 동시에 같은 이름이 등록되면 unique 제약 위반을 중복 오류로 바꾼다. */
    private ClothesAttributeDefinition saveAndFlushUnique(ClothesAttributeDefinition definition) {
        try {
            return definitionRepository.saveAndFlush(definition);
        } catch (DataIntegrityViolationException e) {
            throw new ClothesAttributeDefinitionNameDuplicatedException();
        }
    }

    private void flushUnique() {
        try {
            definitionRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ClothesAttributeDefinitionNameDuplicatedException();
        }
    }
}
