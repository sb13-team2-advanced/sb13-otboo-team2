package codeit.ootd.domain.clothes.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import codeit.ootd.domain.clothes.dto.request.ClothesAttributeDefCreateRequest;
import codeit.ootd.domain.clothes.dto.request.ClothesAttributeDefUpdateRequest;
import codeit.ootd.domain.clothes.dto.response.ClothesAttributeDefDto;
import codeit.ootd.domain.clothes.entity.ClothesAttributeDefinition;
import codeit.ootd.domain.clothes.exception.ClothesAttributeDefinitionNameDuplicatedException;
import codeit.ootd.domain.clothes.exception.ClothesAttributeDefinitionNotFoundException;
import codeit.ootd.domain.clothes.exception.ClothesAttributeValuesDuplicatedException;
import codeit.ootd.domain.clothes.exception.InvalidSortFieldException;
import codeit.ootd.domain.clothes.repository.ClothesAttributeDefinitionRepository;
import codeit.ootd.domain.clothes.repository.ClothesAttributeRepository;
import codeit.ootd.global.enums.SortDirection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ClothesAttributeDefinitionServiceImplTest {

    @Mock
    private ClothesAttributeDefinitionRepository definitionRepository;

    @Mock
    private ClothesAttributeRepository attributeRepository;

    @InjectMocks
    private ClothesAttributeDefinitionServiceImpl service;

    @Test
    void 속성_정의를_등록하면_앞뒤_공백을_제거해_저장한다() {
        given(definitionRepository.existsByName("두께")).willReturn(false);
        given(definitionRepository.saveAndFlush(any(ClothesAttributeDefinition.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ClothesAttributeDefDto result = service.create(
                new ClothesAttributeDefCreateRequest(" 두께 ", List.of(" 얇음", "두꺼움 ")));

        assertThat(result.name()).isEqualTo("두께");
        assertThat(result.selectableValues()).containsExactly("얇음", "두꺼움");
    }

    @Test
    void 이미_있는_이름으로_등록하면_예외가_발생한다() {
        given(definitionRepository.existsByName("두께")).willReturn(true);

        assertThatThrownBy(() -> service.create(
                new ClothesAttributeDefCreateRequest("두께", List.of("얇음"))))
                .isInstanceOf(ClothesAttributeDefinitionNameDuplicatedException.class);

        verify(definitionRepository, never()).saveAndFlush(any());
    }

    @Test
    void 동시에_같은_이름이_저장되어_제약을_위반하면_이름_중복_예외로_바꾼다() {
        given(definitionRepository.existsByName("두께")).willReturn(false);
        given(definitionRepository.saveAndFlush(any(ClothesAttributeDefinition.class)))
                .willThrow(new DataIntegrityViolationException("uk_clothes_attribute_definition_name"));

        assertThatThrownBy(() -> service.create(
                new ClothesAttributeDefCreateRequest("두께", List.of("얇음"))))
                .isInstanceOf(ClothesAttributeDefinitionNameDuplicatedException.class);
    }

    @Test
    void 선택_가능한_값이_중복되면_예외가_발생한다() {
        assertThatThrownBy(() -> service.create(
                new ClothesAttributeDefCreateRequest("두께", List.of("얇음", " 얇음"))))
                .isInstanceOf(ClothesAttributeValuesDuplicatedException.class);

        verify(definitionRepository, never()).saveAndFlush(any());
    }

    @Test
    void 이름만_보내면_선택_가능한_값은_그대로_둔다() {
        UUID id = UUID.randomUUID();
        ClothesAttributeDefinition definition = new ClothesAttributeDefinition("두께", List.of("얇음", "두꺼움"));
        given(definitionRepository.findById(id)).willReturn(Optional.of(definition));
        given(definitionRepository.existsByNameAndIdNot("소재 두께", id)).willReturn(false);

        ClothesAttributeDefDto result = service.update(id, new ClothesAttributeDefUpdateRequest("소재 두께", null));

        assertThat(result.name()).isEqualTo("소재 두께");
        assertThat(result.selectableValues()).containsExactly("얇음", "두꺼움");
    }

    @Test
    void 값만_보내면_이름은_그대로_두고_값_순서를_새로_정한다() {
        UUID id = UUID.randomUUID();
        ClothesAttributeDefinition definition = new ClothesAttributeDefinition("두께", List.of("얇음", "두꺼움"));
        given(definitionRepository.findById(id)).willReturn(Optional.of(definition));

        ClothesAttributeDefDto result = service.update(id,
                new ClothesAttributeDefUpdateRequest(null, List.of("두꺼움", "보통", "얇음")));

        assertThat(result.name()).isEqualTo("두께");
        assertThat(result.selectableValues()).containsExactly("두꺼움", "보통", "얇음");
        verify(definitionRepository, never()).existsByNameAndIdNot(any(), any());
    }

    @Test
    void 이름이_바뀌지_않으면_이름_중복을_검사하지_않는다() {
        UUID id = UUID.randomUUID();
        ClothesAttributeDefinition definition = new ClothesAttributeDefinition("두께", List.of("얇음"));
        given(definitionRepository.findById(id)).willReturn(Optional.of(definition));

        service.update(id, new ClothesAttributeDefUpdateRequest("두께", List.of("얇음", "두꺼움")));

        verify(definitionRepository, never()).existsByNameAndIdNot(any(), any());
    }

    @Test
    void 다른_정의가_쓰는_이름으로_바꾸면_예외가_발생한다() {
        UUID id = UUID.randomUUID();
        ClothesAttributeDefinition definition = new ClothesAttributeDefinition("두께", List.of("얇음"));
        given(definitionRepository.findById(id)).willReturn(Optional.of(definition));
        given(definitionRepository.existsByNameAndIdNot("색상", id)).willReturn(true);

        assertThatThrownBy(() -> service.update(id, new ClothesAttributeDefUpdateRequest("색상", null)))
                .isInstanceOf(ClothesAttributeDefinitionNameDuplicatedException.class);
        assertThat(definition.getName()).isEqualTo("두께");
    }

    @Test
    void 없는_속성_정의를_수정하면_예외가_발생한다() {
        UUID id = UUID.randomUUID();
        given(definitionRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, new ClothesAttributeDefUpdateRequest("두께", null)))
                .isInstanceOf(ClothesAttributeDefinitionNotFoundException.class);
    }

    @Test
    void 속성_정의를_삭제하면_의상에_붙은_속성_값을_먼저_지운다() {
        UUID id = UUID.randomUUID();
        ClothesAttributeDefinition definition = new ClothesAttributeDefinition("두께", List.of("얇음"));
        given(definitionRepository.findById(id)).willReturn(Optional.of(definition));

        service.delete(id);

        InOrder inOrder = inOrder(attributeRepository, definitionRepository);
        inOrder.verify(attributeRepository).deleteAllByDefinitionId(id);
        inOrder.verify(definitionRepository).delete(definition);
    }

    @Test
    void 없는_속성_정의를_삭제하면_예외가_발생하고_아무것도_지우지_않는다() {
        UUID id = UUID.randomUUID();
        given(definitionRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ClothesAttributeDefinitionNotFoundException.class);

        verify(attributeRepository, never()).deleteAllByDefinitionId(any());
        verify(definitionRepository, never()).delete(any());
    }

    @Test
    void 목록은_정렬_기준과_방향대로_id를_보조_기준으로_조회한다() {
        Sort sort = Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id"));
        given(definitionRepository.findAll(sort)).willReturn(List.of(
                definition("가방", UUID.randomUUID()), definition("나이", UUID.randomUUID())));

        List<ClothesAttributeDefDto> result = service.list("name", SortDirection.ASCENDING, null);

        assertThat(result).extracting(ClothesAttributeDefDto::name).containsExactly("가방", "나이");
    }

    @Test
    void 검색어가_있으면_공백을_제거하고_이름으로_검색한다() {
        Sort sort = Sort.by(Sort.Direction.DESC, "name").and(Sort.by(Sort.Direction.DESC, "id"));
        given(definitionRepository.findAllByNameContainingIgnoreCase("두", sort))
                .willReturn(List.of(definition("두께", UUID.randomUUID())));

        List<ClothesAttributeDefDto> result = service.list("name", SortDirection.DESCENDING, " 두 ");

        assertThat(result).extracting(ClothesAttributeDefDto::name).containsExactly("두께");
        verify(definitionRepository, never()).findAll(any(Sort.class));
    }

    @Test
    void 정렬_기준과_방향이_없으면_생성일_내림차순이고_빈_검색어는_무시한다() {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        given(definitionRepository.findAll(sort)).willReturn(List.of());

        List<ClothesAttributeDefDto> result = service.list(null, null, "  ");

        assertThat(result).isEmpty();
        verify(definitionRepository, never()).findAllByNameContainingIgnoreCase(any(), any());
    }

    @Test
    void 지원하지_않는_정렬_기준이면_조회하지_않고_예외가_발생한다() {
        assertThatThrownBy(() -> service.list("likeCount", SortDirection.ASCENDING, null))
                .isInstanceOf(InvalidSortFieldException.class);

        verify(definitionRepository, never()).findAll(any(Sort.class));
    }

    private ClothesAttributeDefinition definition(String name, UUID id) {
        ClothesAttributeDefinition definition = new ClothesAttributeDefinition(name, List.of("값"));
        ReflectionTestUtils.setField(definition, "id", id);
        return definition;
    }
}
