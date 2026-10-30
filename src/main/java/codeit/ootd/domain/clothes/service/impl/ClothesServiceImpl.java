package codeit.ootd.domain.clothes.service.impl;

import codeit.ootd.domain.clothes.dto.request.ClothesAttributeRequest;
import codeit.ootd.domain.clothes.dto.request.ClothesCreateRequest;
import codeit.ootd.domain.clothes.dto.request.ClothesUpdateRequest;
import codeit.ootd.domain.clothes.dto.response.ClothesAttributeWithDefDto;
import codeit.ootd.domain.clothes.dto.response.ClothesDto;
import codeit.ootd.domain.clothes.dto.response.ClothesDtoCursorResponse;
import codeit.ootd.domain.clothes.entity.Clothes;
import codeit.ootd.domain.clothes.entity.ClothesAttribute;
import codeit.ootd.domain.clothes.entity.ClothesAttributeDefinition;
import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.clothes.exception.ClothesAttributeDefinitionNotFoundException;
import codeit.ootd.domain.clothes.exception.ClothesAttributeDuplicatedException;
import codeit.ootd.domain.clothes.exception.ClothesAttributeValueInvalidException;
import codeit.ootd.domain.clothes.exception.ClothesInUseException;
import codeit.ootd.domain.clothes.exception.ClothesInvalidCursorException;
import codeit.ootd.domain.clothes.exception.ClothesNotFoundException;
import codeit.ootd.domain.clothes.exception.ClothesOwnerNotFoundException;
import codeit.ootd.domain.clothes.repository.ClothesAttributeDefinitionRepository;
import codeit.ootd.domain.clothes.repository.ClothesAttributeRepository;
import codeit.ootd.domain.clothes.repository.ClothesQueryRepository;
import codeit.ootd.domain.clothes.repository.ClothesRepository;
import codeit.ootd.domain.clothes.service.ClothesService;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.global.enums.SortDirection;
import codeit.ootd.infra.storage.FileStorage;
import codeit.ootd.infra.storage.TransactionalFileCleaner;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClothesServiceImpl implements ClothesService {

    private static final String IMAGE_DIRECTORY = "clothes";
    private static final int MAX_LIMIT = 100;

    private final ClothesRepository clothesRepository;
    private final ClothesQueryRepository clothesQueryRepository;
    private final ClothesAttributeRepository attributeRepository;
    private final ClothesAttributeDefinitionRepository definitionRepository;
    private final UserRepository userRepository;
    private final FileStorage fileStorage;
    private final TransactionalFileCleaner fileCleaner;

    @Override
    @Transactional
    public ClothesDto create(ClothesCreateRequest request, MultipartFile image) {
        if (!userRepository.existsById(request.ownerId())) {
            throw new ClothesOwnerNotFoundException();
        }

        // 요청 검증을 끝낸 뒤에 파일을 올려, 검증 실패로 파일이 남는 일을 막는다.
        List<ClothesAttributeRequest> attributes = orEmpty(request.attributes());
        validateAttributes(attributes);

        String imageUrl = hasFile(image) ? fileStorage.upload(image, IMAGE_DIRECTORY) : null;
        // 이후 저장이나 커밋이 실패해 롤백되면 방금 올린 파일을 정리한다.
        fileCleaner.deleteOnRollback(imageUrl);

        Clothes clothes = clothesRepository.save(
                new Clothes(request.ownerId(), request.name().strip(), request.type(), imageUrl));
        attributeRepository.saveAll(attributes.stream()
                .map(a -> new ClothesAttribute(clothes.getId(), a.definitionId(), a.value().strip()))
                .toList());

        ClothesDto dto = assemble(List.of(clothes)).get(0);
        log.info("의상 등록 완료: clothesId={}", clothes.getId());
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public ClothesDtoCursorResponse list(UUID ownerId, ClothesType typeEqual, String cursor, UUID idAfter,
            int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ClothesInvalidCursorException();
        }
        Instant cursorTime = parseCursor(cursor);

        // 다음 페이지가 있는지 알기 위해 한 건 더 조회한다.
        List<Clothes> rows = clothesQueryRepository.findList(ownerId, typeEqual, cursorTime, idAfter, limit + 1);
        boolean hasNext = rows.size() > limit;
        List<Clothes> page = hasNext ? rows.subList(0, limit) : rows;

        String nextCursor = null;
        UUID nextIdAfter = null;
        if (hasNext) {
            Clothes last = page.get(page.size() - 1);
            nextCursor = last.getCreatedAt().toString();
            nextIdAfter = last.getId();
        }

        long totalCount = clothesQueryRepository.countList(ownerId, typeEqual);
        return new ClothesDtoCursorResponse(assemble(page), nextCursor, nextIdAfter, hasNext, totalCount,
                "createdAt", SortDirection.DESCENDING);
    }

    @Override
    @Transactional
    public ClothesDto update(UUID clothesId, ClothesUpdateRequest request, MultipartFile image) {
        Clothes clothes = clothesRepository.findById(clothesId).orElseThrow(ClothesNotFoundException::new);

        List<ClothesAttributeRequest> attributes = request.attributes();
        if (attributes != null) {
            validateAttributes(attributes);
        }

        String name = request.name() == null ? clothes.getName() : request.name().strip();
        ClothesType type = request.type() == null ? clothes.getType() : request.type();
        String oldImageUrl = clothes.getImageUrl();
        String newImageUrl = hasFile(image) ? fileStorage.upload(image, IMAGE_DIRECTORY) : null;
        // 롤백되면 새로 올린 파일을 정리하고, 이전 파일은 커밋이 끝난 뒤에만 지운다.
        fileCleaner.deleteOnRollback(newImageUrl);

        clothes.update(name, type);
        if (newImageUrl != null) {
            clothes.changeImageUrl(newImageUrl);
        }
        if (attributes != null) {
            syncAttributes(clothes.getId(), attributes);
        }
        ClothesDto dto = assemble(List.of(clothes)).get(0);

        if (newImageUrl != null) {
            fileCleaner.deleteAfterCommit(oldImageUrl);
        }
        log.info("의상 수정 완료: clothesId={}", clothesId);
        return dto;
    }

    @Override
    @Transactional
    public void delete(UUID clothesId) {
        Clothes clothes = clothesRepository.findById(clothesId).orElseThrow(ClothesNotFoundException::new);

        if (clothesRepository.existsInFeed(clothesId)) {
            throw new ClothesInUseException();
        }

        try {
            attributeRepository.deleteAllByClothesId(clothesId);
            clothesRepository.delete(clothes);
            clothesRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // 확인 직후 다른 요청이 피드에 의상을 추가한 경우 FK 위반으로 막힌다.
            throw new ClothesInUseException();
        }

        // DB 삭제가 커밋된 뒤에만 이미지 파일을 지운다.
        fileCleaner.deleteAfterCommit(clothes.getImageUrl());
        log.info("의상 삭제 완료: clothesId={}", clothesId);
    }

    /**
     * 요청한 속성이 모두 존재하는 정의이고, 값이 그 정의의 선택 가능한 값 안에 있는지 검증한다.
     */
    private void validateAttributes(List<ClothesAttributeRequest> attributes) {
        if (attributes.isEmpty()) {
            return;
        }

        Set<UUID> definitionIds = new HashSet<>();
        for (ClothesAttributeRequest attribute : attributes) {
            if (!definitionIds.add(attribute.definitionId())) {
                throw new ClothesAttributeDuplicatedException();
            }
        }

        Map<UUID, ClothesAttributeDefinition> definitions = definitionRepository.findAllById(definitionIds).stream()
                .collect(Collectors.toMap(ClothesAttributeDefinition::getId, Function.identity()));

        for (ClothesAttributeRequest attribute : attributes) {
            ClothesAttributeDefinition definition = definitions.get(attribute.definitionId());
            if (definition == null) {
                throw new ClothesAttributeDefinitionNotFoundException();
            }
            if (!definition.getSelectableValues().contains(attribute.value().strip())) {
                throw new ClothesAttributeValueInvalidException();
            }
        }
    }

    /**
     * 의상의 속성을 요청 내용과 같아지도록 맞춘다. 삭제 후 재등록하면 같은 flush 안에서 INSERT가 먼저 실행되어
     * (clothes_id, definition_id) 유니크 제약에 걸리므로, 기존 행은 값만 바꾸고 없어진 정의만 삭제한다.
     */
    private void syncAttributes(UUID clothesId, List<ClothesAttributeRequest> requested) {
        Map<UUID, String> requestedValues = new LinkedHashMap<>();
        requested.forEach(a -> requestedValues.put(a.definitionId(), a.value().strip()));

        Map<UUID, ClothesAttribute> existing = attributeRepository.findAllByClothesId(clothesId).stream()
                .collect(Collectors.toMap(ClothesAttribute::getDefinitionId, Function.identity()));

        existing.forEach((definitionId, attribute) -> {
            String value = requestedValues.get(definitionId);
            if (value == null) {
                attributeRepository.delete(attribute);
            } else if (!value.equals(attribute.getValue())) {
                attribute.changeValue(value);
            }
        });

        requestedValues.forEach((definitionId, value) -> {
            if (!existing.containsKey(definitionId)) {
                attributeRepository.save(new ClothesAttribute(clothesId, definitionId, value));
            }
        });
    }

    /**
     * 의상 목록을 응답 DTO로 만든다. 속성과 속성 정의는 의상 수와 관계없이 한 번씩만 조회한다.
     */
    private List<ClothesDto> assemble(List<Clothes> clothesList) {
        if (clothesList.isEmpty()) {
            return List.of();
        }

        List<UUID> clothesIds = clothesList.stream().map(Clothes::getId).toList();
        List<ClothesAttribute> attributes = attributeRepository.findAllByClothesIdIn(clothesIds);

        Set<UUID> definitionIds = attributes.stream().map(ClothesAttribute::getDefinitionId)
                .collect(Collectors.toSet());
        Map<UUID, ClothesAttributeDefinition> definitions = definitionRepository.findAllById(definitionIds).stream()
                .collect(Collectors.toMap(ClothesAttributeDefinition::getId, Function.identity()));

        Map<UUID, List<ClothesAttribute>> attributesByClothes = new HashMap<>();
        attributes.forEach(a -> attributesByClothes.computeIfAbsent(a.getClothesId(), k -> new ArrayList<>()).add(a));

        // 속성 순서는 속성 정의가 만들어진 순서로 고정한다.
        Comparator<ClothesAttributeDefinition> byCreated = Comparator
                .comparing(ClothesAttributeDefinition::getCreatedAt)
                .thenComparing(ClothesAttributeDefinition::getId);

        return clothesList.stream().map(clothes -> {
            List<ClothesAttributeWithDefDto> attributeDtos = attributesByClothes
                    .getOrDefault(clothes.getId(), List.of()).stream()
                    .filter(a -> definitions.containsKey(a.getDefinitionId()))
                    .sorted(Comparator.<ClothesAttribute, ClothesAttributeDefinition>comparing(
                            a -> definitions.get(a.getDefinitionId()), byCreated))
                    .map(a -> {
                        ClothesAttributeDefinition definition = definitions.get(a.getDefinitionId());
                        return new ClothesAttributeWithDefDto(definition.getId(), definition.getName(),
                                List.copyOf(definition.getSelectableValues()), a.getValue());
                    })
                    .toList();
            return new ClothesDto(clothes.getId(), clothes.getUserId(), clothes.getName(), clothes.getImageUrl(),
                    clothes.getType(), attributeDtos);
        }).toList();
    }

    private Instant parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(cursor);
        } catch (DateTimeParseException e) {
            throw new ClothesInvalidCursorException();
        }
    }

    private boolean hasFile(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    private List<ClothesAttributeRequest> orEmpty(List<ClothesAttributeRequest> attributes) {
        return attributes == null ? List.of() : attributes;
    }
}
