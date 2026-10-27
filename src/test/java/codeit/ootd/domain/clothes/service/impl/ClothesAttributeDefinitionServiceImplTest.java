package codeit.ootd.domain.clothes.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
    void 목록은_한_건_더_조회해서_다음_페이지_여부와_커서를_만든다() {
        ClothesAttributeDefinition a = definition("가방", UUID.randomUUID());
        ClothesAttributeDefinition b = definition("나이", UUID.randomUUID());
        ClothesAttributeDefinition c = definition("다리", UUID.randomUUID());
        given(definitionRepository.findPage(isNull(), eq(ClothesAttributeDefSortBy.NAME),
                eq(SortDirection.ASCENDING), isNull(), isNull(), eq(3))).willReturn(List.of(a, b, c));
        given(definitionRepository.countByKeyword(null)).willReturn(7L);

        ClothesAttributeDefDtoCursorResponse result =
                service.list(null, null, 2, "name", SortDirection.ASCENDING, null);

        assertThat(result.data()).extracting(ClothesAttributeDefDto::name).containsExactly("가방", "나이");
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isEqualTo("나이");
        assertThat(result.nextIdAfter()).isEqualTo(b.getId());
        assertThat(result.totalCount()).isEqualTo(7L);
        assertThat(result.sortBy()).isEqualTo("name");
        assertThat(result.sortDirection()).isEqualTo(SortDirection.ASCENDING);
    }

    @Test
    void 마지막_페이지는_다음_커서가_없다() {
        ClothesAttributeDefinition a = definition("가방", UUID.randomUUID());
        given(definitionRepository.findPage(any(), any(), any(), any(), any(), anyInt()))
                .willReturn(List.of(a));
        given(definitionRepository.countByKeyword(null)).willReturn(1L);

        ClothesAttributeDefDtoCursorResponse result =
                service.list(null, null, 10, "name", SortDirection.ASCENDING, null);

        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.nextIdAfter()).isNull();
    }

    @Test
    void 정렬_기준과_방향이_없으면_생성일_내림차순이고_빈_검색어는_무시한다() {
        given(definitionRepository.findPage(any(), any(), any(), any(), any(), anyInt()))
                .willReturn(List.of());
        given(definitionRepository.countByKeyword(null)).willReturn(0L);

        ClothesAttributeDefDtoCursorResponse result = service.list(null, null, 10, null, null, "  ");

        assertThat(result.sortBy()).isEqualTo("createdAt");
        assertThat(result.sortDirection()).isEqualTo(SortDirection.DESCENDING);
        verify(definitionRepository).findPage(isNull(), eq(ClothesAttributeDefSortBy.CREATED_AT),
                eq(SortDirection.DESCENDING), isNull(), isNull(), eq(11));
    }

    @Test
    void 커서와_보조_커서는_함께_있어야_한다() {
        assertThatThrownBy(() -> service.list("가방", null, 10, "name", SortDirection.ASCENDING, null))
                .isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> service.list(null, UUID.randomUUID(), 10, "name", SortDirection.ASCENDING, null))
                .isInstanceOf(InvalidCursorException.class);
    }

    private ClothesAttributeDefinition definition(String name, UUID id) {
        ClothesAttributeDefinition definition = new ClothesAttributeDefinition(name, List.of("값"));
        ReflectionTestUtils.setField(definition, "id", id);
        return definition;
    }
}
