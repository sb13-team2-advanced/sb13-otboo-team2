package codeit.ootd.domain.clothes.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.global.enums.SortDirection;
import codeit.ootd.infra.storage.FileStorage;
import codeit.ootd.infra.storage.TransactionalFileCleaner;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class ClothesServiceImplTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID CLOTHES_ID = UUID.randomUUID();
    private static final Instant T0 = Instant.parse("2026-10-28T00:00:00Z");

    @Mock
    private ClothesRepository clothesRepository;
    @Mock
    private ClothesQueryRepository clothesQueryRepository;
    @Mock
    private ClothesAttributeRepository attributeRepository;
    @Mock
    private ClothesAttributeDefinitionRepository definitionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FileStorage fileStorage;

    private ClothesServiceImpl service;

    @Captor
    private ArgumentCaptor<Iterable<ClothesAttribute>> attributesCaptor;
    @Captor
    private ArgumentCaptor<ClothesAttribute> attributeCaptor;

    @BeforeEach
    void setUp() {
        // 서비스가 등록하는 커밋·롤백 후 처리를 직접 실행해 볼 수 있도록 트랜잭션 동기화를 켜 둔다.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.initSynchronization();
        service = new ClothesServiceImpl(clothesRepository, clothesQueryRepository, attributeRepository,
                definitionRepository, userRepository, fileStorage, new TransactionalFileCleaner(fileStorage));
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    // ---------- 등록 ----------

    @Test
    void 속성과_이미지_없이_등록하면_이름을_다듬어_저장하고_업로드하지_않는다() {
        given(userRepository.existsById(OWNER_ID)).willReturn(true);
        givenSaveAssignsId();

        ClothesDto dto = service.create(
                new ClothesCreateRequest(OWNER_ID, " 셔츠 ", ClothesType.TOP, null), null);

        assertThat(dto.id()).isEqualTo(CLOTHES_ID);
        assertThat(dto.ownerId()).isEqualTo(OWNER_ID);
        assertThat(dto.name()).isEqualTo("셔츠");
        assertThat(dto.type()).isEqualTo(ClothesType.TOP);
        assertThat(dto.imageUrl()).isNull();
        assertThat(dto.attributes()).isEmpty();
        verifyNoInteractions(fileStorage);
    }

    @Test
    void 속성과_이미지를_함께_등록하면_응답에_속성_정의_정보가_담긴다() {
        ClothesAttributeDefinition color = definition("색상", T0, "검정", "흰색");
        MockMultipartFile image = image();
        given(userRepository.existsById(OWNER_ID)).willReturn(true);
        given(definitionRepository.findAllById(any())).willReturn(List.of(color));
        given(fileStorage.upload(image, "clothes")).willReturn("/files/clothes/a.png");
        givenSaveAssignsId();
        given(attributeRepository.findAllByClothesIdIn(any()))
                .willReturn(List.of(new ClothesAttribute(CLOTHES_ID, color.getId(), "검정")));

        ClothesDto dto = service.create(new ClothesCreateRequest(OWNER_ID, "셔츠", ClothesType.TOP,
                List.of(new ClothesAttributeRequest(color.getId(), " 검정 "))), image);

        assertThat(dto.imageUrl()).isEqualTo("/files/clothes/a.png");
        assertThat(dto.attributes()).hasSize(1);
        ClothesAttributeWithDefDto attribute = dto.attributes().get(0);
        assertThat(attribute.definitionId()).isEqualTo(color.getId());
        assertThat(attribute.definitionName()).isEqualTo("색상");
        assertThat(attribute.selectableValues()).containsExactly("검정", "흰색");
        assertThat(attribute.value()).isEqualTo("검정");

        verify(attributeRepository).saveAll(attributesCaptor.capture());
        assertThat(attributesCaptor.getValue()).extracting(ClothesAttribute::getValue).containsExactly("검정");
    }

    @Test
    void 소유자가_없으면_예외가_나고_아무것도_저장하지_않는다() {
        given(userRepository.existsById(OWNER_ID)).willReturn(false);

        assertThatThrownBy(() -> service.create(
                new ClothesCreateRequest(OWNER_ID, "셔츠", ClothesType.TOP, null), image()))
                .isInstanceOf(ClothesOwnerNotFoundException.class);

        verifyNoInteractions(clothesRepository, fileStorage);
    }

    @Test
    void 같은_속성_정의를_두_번_보내면_예외가_나고_업로드하지_않는다() {
        UUID definitionId = UUID.randomUUID();
        given(userRepository.existsById(OWNER_ID)).willReturn(true);

        assertThatThrownBy(() -> service.create(new ClothesCreateRequest(OWNER_ID, "셔츠", ClothesType.TOP,
                List.of(new ClothesAttributeRequest(definitionId, "검정"),
                        new ClothesAttributeRequest(definitionId, "흰색"))), image()))
                .isInstanceOf(ClothesAttributeDuplicatedException.class);

        verifyNoInteractions(clothesRepository, fileStorage);
    }

    @Test
    void 존재하지_않는_속성_정의면_예외가_나고_업로드하지_않는다() {
        given(userRepository.existsById(OWNER_ID)).willReturn(true);
        given(definitionRepository.findAllById(any())).willReturn(List.of());

        assertThatThrownBy(() -> service.create(new ClothesCreateRequest(OWNER_ID, "셔츠", ClothesType.TOP,
                List.of(new ClothesAttributeRequest(UUID.randomUUID(), "검정"))), image()))
                .isInstanceOf(ClothesAttributeDefinitionNotFoundException.class);

        verifyNoInteractions(clothesRepository, fileStorage);
    }

    @Test
    void 선택_가능한_값에_없는_값이면_예외가_나고_업로드하지_않는다() {
        ClothesAttributeDefinition color = definition("색상", T0, "검정", "흰색");
        given(userRepository.existsById(OWNER_ID)).willReturn(true);
        given(definitionRepository.findAllById(any())).willReturn(List.of(color));

        assertThatThrownBy(() -> service.create(new ClothesCreateRequest(OWNER_ID, "셔츠", ClothesType.TOP,
                List.of(new ClothesAttributeRequest(color.getId(), "보라"))), image()))
                .isInstanceOf(ClothesAttributeValueInvalidException.class);

        verifyNoInteractions(clothesRepository, fileStorage);
    }

    @Test
    void 저장_중_예외로_롤백되면_업로드한_이미지를_삭제한다() {
        MockMultipartFile image = image();
        given(userRepository.existsById(OWNER_ID)).willReturn(true);
        given(fileStorage.upload(image, "clothes")).willReturn("/files/clothes/a.png");
        given(clothesRepository.save(any(Clothes.class))).willThrow(new IllegalStateException("db"));

        assertThatThrownBy(() -> service.create(
                new ClothesCreateRequest(OWNER_ID, "셔츠", ClothesType.TOP, null), image))
                .isInstanceOf(IllegalStateException.class);
        verify(fileStorage, never()).delete(any());

        rollback();

        verify(fileStorage).delete("/files/clothes/a.png");
    }

    @Test
    void 등록이_커밋되면_업로드한_이미지는_삭제하지_않는다() {
        MockMultipartFile image = image();
        given(userRepository.existsById(OWNER_ID)).willReturn(true);
        given(fileStorage.upload(image, "clothes")).willReturn("/files/clothes/a.png");
        givenSaveAssignsId();

        service.create(new ClothesCreateRequest(OWNER_ID, "셔츠", ClothesType.TOP, null), image);
        commit();

        verify(fileStorage, never()).delete(any());
    }

    // ---------- 목록 ----------

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101})
    void 조회_개수가_범위를_벗어나면_예외가_난다(int limit) {
        assertThatThrownBy(() -> service.list(OWNER_ID, null, null, null, limit))
                .isInstanceOf(ClothesInvalidCursorException.class);

        verifyNoInteractions(clothesQueryRepository);
    }

    @Test
    void 커서_형식이_잘못되면_예외가_난다() {
        assertThatThrownBy(() -> service.list(OWNER_ID, null, "not-a-time", null, 20))
                .isInstanceOf(ClothesInvalidCursorException.class);

        verifyNoInteractions(clothesQueryRepository);
    }

    @Test
    void 다음_페이지가_있으면_limit개만_돌려주고_다음_커서를_채운다() {
        Clothes newest = clothes(UUID.randomUUID(), "c3", T0.plusSeconds(3));
        Clothes middle = clothes(UUID.randomUUID(), "c2", T0.plusSeconds(2));
        Clothes oldest = clothes(UUID.randomUUID(), "c1", T0.plusSeconds(1));
        given(clothesQueryRepository.findList(OWNER_ID, null, null, null, 3))
                .willReturn(List.of(newest, middle, oldest));
        given(clothesQueryRepository.countList(OWNER_ID, null)).willReturn(5L);

        ClothesDtoCursorResponse response = service.list(OWNER_ID, null, null, null, 2);

        assertThat(response.data()).extracting(ClothesDto::name).containsExactly("c3", "c2");
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo(middle.getCreatedAt().toString());
        assertThat(response.nextIdAfter()).isEqualTo(middle.getId());
        assertThat(response.totalCount()).isEqualTo(5L);
        assertThat(response.sortBy()).isEqualTo("createdAt");
        assertThat(response.sortDirection()).isEqualTo(SortDirection.DESCENDING);
    }

    @Test
    void 마지막_페이지면_다음_커서가_비어_있다() {
        Clothes newest = clothes(UUID.randomUUID(), "c2", T0.plusSeconds(2));
        Clothes oldest = clothes(UUID.randomUUID(), "c1", T0.plusSeconds(1));
        given(clothesQueryRepository.findList(OWNER_ID, null, null, null, 3))
                .willReturn(List.of(newest, oldest));
        given(clothesQueryRepository.countList(OWNER_ID, null)).willReturn(2L);

        ClothesDtoCursorResponse response = service.list(OWNER_ID, null, null, null, 2);

        assertThat(response.data()).hasSize(2);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.nextIdAfter()).isNull();
    }

    @Test
    void 커서와_타입_조건은_파싱해서_그대로_조회에_넘긴다() {
        UUID idAfter = UUID.randomUUID();
        Instant cursor = Instant.parse("2026-10-28T09:00:00Z");
        given(clothesQueryRepository.findList(OWNER_ID, ClothesType.TOP, cursor, idAfter, 21))
                .willReturn(List.of());
        given(clothesQueryRepository.countList(OWNER_ID, ClothesType.TOP)).willReturn(0L);

        ClothesDtoCursorResponse response = service.list(
                OWNER_ID, ClothesType.TOP, "2026-10-28T09:00:00Z", idAfter, 20);

        assertThat(response.data()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.totalCount()).isZero();
    }

    // ---------- 수정 ----------

    @Test
    void 수정할_의상이_없으면_예외가_난다() {
        given(clothesRepository.findById(CLOTHES_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(CLOTHES_ID, new ClothesUpdateRequest("새 이름", null, null), null))
                .isInstanceOf(ClothesNotFoundException.class);
    }

    @Test
    void 일부_필드만_보내면_나머지는_유지하고_속성과_이미지는_건드리지_않는다() {
        Clothes existing = existingClothes("/files/clothes/old.png");
        given(clothesRepository.findById(CLOTHES_ID)).willReturn(Optional.of(existing));

        ClothesDto dto = service.update(CLOTHES_ID, new ClothesUpdateRequest(" 새 이름 ", null, null), null);

        assertThat(dto.name()).isEqualTo("새 이름");
        assertThat(dto.type()).isEqualTo(ClothesType.TOP);
        assertThat(dto.imageUrl()).isEqualTo("/files/clothes/old.png");
        verify(attributeRepository, never()).findAllByClothesId(any());
        verifyNoInteractions(fileStorage);
    }

    @Test
    void 속성을_보내면_값은_바꾸고_빠진_속성은_삭제하고_새_속성은_추가한다() {
        ClothesAttributeDefinition color = definition("색상", T0, "검정", "흰색");
        ClothesAttributeDefinition size = definition("사이즈", T0.plusSeconds(1), "S", "M");
        ClothesAttributeDefinition material = definition("소재", T0.plusSeconds(2), "면", "울");
        ClothesAttribute colorAttribute = new ClothesAttribute(CLOTHES_ID, color.getId(), "검정");
        ClothesAttribute sizeAttribute = new ClothesAttribute(CLOTHES_ID, size.getId(), "M");

        given(clothesRepository.findById(CLOTHES_ID)).willReturn(Optional.of(existingClothes(null)));
        given(definitionRepository.findAllById(any())).willReturn(List.of(color, material));
        given(attributeRepository.findAllByClothesId(CLOTHES_ID)).willReturn(List.of(colorAttribute, sizeAttribute));
        given(attributeRepository.findAllByClothesIdIn(any())).willReturn(List.of(colorAttribute));

        ClothesDto dto = service.update(CLOTHES_ID, new ClothesUpdateRequest(null, null, List.of(
                new ClothesAttributeRequest(color.getId(), "흰색"),
                new ClothesAttributeRequest(material.getId(), "면"))), null);

        assertThat(colorAttribute.getValue()).isEqualTo("흰색");
        verify(attributeRepository).delete(sizeAttribute);
        verify(attributeRepository).save(attributeCaptor.capture());
        assertThat(attributeCaptor.getValue().getDefinitionId()).isEqualTo(material.getId());
        assertThat(attributeCaptor.getValue().getValue()).isEqualTo("면");
        assertThat(dto.attributes()).extracting(ClothesAttributeWithDefDto::definitionName).containsExactly("색상");
    }

    @Test
    void 속성을_빈_목록으로_보내면_모든_속성이_삭제된다() {
        ClothesAttribute attribute = new ClothesAttribute(CLOTHES_ID, UUID.randomUUID(), "검정");
        given(clothesRepository.findById(CLOTHES_ID)).willReturn(Optional.of(existingClothes(null)));
        given(attributeRepository.findAllByClothesId(CLOTHES_ID)).willReturn(List.of(attribute));

        service.update(CLOTHES_ID, new ClothesUpdateRequest(null, null, List.of()), null);

        verify(attributeRepository).delete(attribute);
        verify(attributeRepository, never()).save(any(ClothesAttribute.class));
    }

    @Test
    void 새_이미지를_올리면_커밋된_뒤에만_이전_이미지를_삭제한다() {
        MockMultipartFile image = image();
        given(clothesRepository.findById(CLOTHES_ID))
                .willReturn(Optional.of(existingClothes("/files/clothes/old.png")));
        given(fileStorage.upload(image, "clothes")).willReturn("/files/clothes/new.png");

        ClothesDto dto = service.update(CLOTHES_ID, new ClothesUpdateRequest(null, null, null), image);

        assertThat(dto.imageUrl()).isEqualTo("/files/clothes/new.png");
        verify(fileStorage, never()).delete(any());

        commit();

        verify(fileStorage).delete("/files/clothes/old.png");
        verify(fileStorage, never()).delete("/files/clothes/new.png");
    }

    @Test
    void 수정이_롤백되면_새_이미지만_삭제하고_이전_이미지는_그대로_둔다() {
        MockMultipartFile image = image();
        given(clothesRepository.findById(CLOTHES_ID))
                .willReturn(Optional.of(existingClothes("/files/clothes/old.png")));
        given(fileStorage.upload(image, "clothes")).willReturn("/files/clothes/new.png");

        service.update(CLOTHES_ID, new ClothesUpdateRequest(null, null, null), image);
        rollback();

        verify(fileStorage).delete("/files/clothes/new.png");
        verify(fileStorage, never()).delete("/files/clothes/old.png");
    }

    @Test
    void 수정_중_예외가_나면_새_이미지만_삭제하고_이전_이미지는_그대로_둔다() {
        ClothesAttributeDefinition color = definition("색상", T0, "검정", "흰색");
        MockMultipartFile image = image();
        given(clothesRepository.findById(CLOTHES_ID))
                .willReturn(Optional.of(existingClothes("/files/clothes/old.png")));
        given(definitionRepository.findAllById(any())).willReturn(List.of(color));
        given(fileStorage.upload(image, "clothes")).willReturn("/files/clothes/new.png");
        given(attributeRepository.findAllByClothesId(CLOTHES_ID)).willThrow(new IllegalStateException("db"));

        assertThatThrownBy(() -> service.update(CLOTHES_ID, new ClothesUpdateRequest(null, null,
                List.of(new ClothesAttributeRequest(color.getId(), "검정"))), image))
                .isInstanceOf(IllegalStateException.class);

        rollback();

        verify(fileStorage).delete("/files/clothes/new.png");
        verify(fileStorage, never()).delete("/files/clothes/old.png");
    }

    @Test
    void 잘못된_속성_값이면_예외가_나고_이미지는_업로드하지_않는다() {
        ClothesAttributeDefinition color = definition("색상", T0, "검정", "흰색");
        given(clothesRepository.findById(CLOTHES_ID)).willReturn(Optional.of(existingClothes(null)));
        given(definitionRepository.findAllById(any())).willReturn(List.of(color));

        assertThatThrownBy(() -> service.update(CLOTHES_ID, new ClothesUpdateRequest(null, null,
                List.of(new ClothesAttributeRequest(color.getId(), "보라"))), image()))
                .isInstanceOf(ClothesAttributeValueInvalidException.class);

        verifyNoInteractions(fileStorage);
    }

    // ---------- 삭제 ----------

    @Test
    void 삭제할_의상이_없으면_예외가_난다() {
        given(clothesRepository.findById(CLOTHES_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(CLOTHES_ID)).isInstanceOf(ClothesNotFoundException.class);
    }

    @Test
    void 피드에서_사용_중인_의상은_삭제하지_않고_예외가_난다() {
        given(clothesRepository.findById(CLOTHES_ID))
                .willReturn(Optional.of(existingClothes("/files/clothes/old.png")));
        given(clothesRepository.existsInFeed(CLOTHES_ID)).willReturn(true);

        assertThatThrownBy(() -> service.delete(CLOTHES_ID)).isInstanceOf(ClothesInUseException.class);

        verify(clothesRepository, never()).delete(any(Clothes.class));
        verifyNoInteractions(attributeRepository, fileStorage);
    }

    @Test
    void 삭제하면_속성과_의상을_지우고_커밋된_뒤에_이미지를_삭제한다() {
        Clothes existing = existingClothes("/files/clothes/old.png");
        given(clothesRepository.findById(CLOTHES_ID)).willReturn(Optional.of(existing));

        service.delete(CLOTHES_ID);

        InOrder order = inOrder(attributeRepository, clothesRepository);
        order.verify(attributeRepository).deleteAllByClothesId(CLOTHES_ID);
        order.verify(clothesRepository).delete(existing);
        order.verify(clothesRepository).flush();
        verifyNoInteractions(fileStorage);

        commit();

        verify(fileStorage).delete("/files/clothes/old.png");
    }

    @Test
    void 삭제가_롤백되면_이미지를_삭제하지_않는다() {
        given(clothesRepository.findById(CLOTHES_ID))
                .willReturn(Optional.of(existingClothes("/files/clothes/old.png")));

        service.delete(CLOTHES_ID);
        rollback();

        verifyNoInteractions(fileStorage);
    }

    @Test
    void 삭제_중_FK_위반이_나면_사용_중_예외로_바꾸고_이미지는_지우지_않는다() {
        given(clothesRepository.findById(CLOTHES_ID))
                .willReturn(Optional.of(existingClothes("/files/clothes/old.png")));
        doThrow(new DataIntegrityViolationException("fk")).when(clothesRepository).flush();

        assertThatThrownBy(() -> service.delete(CLOTHES_ID)).isInstanceOf(ClothesInUseException.class);

        verifyNoInteractions(fileStorage);
    }

    // ---------- 준비 ----------

    private void commit() {
        List<TransactionSynchronization> synchronizations =
                new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    }

    private void rollback() {
        List<TransactionSynchronization> synchronizations =
                new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
        synchronizations.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }

    private void givenSaveAssignsId() {
        given(clothesRepository.save(any(Clothes.class))).willAnswer(invocation -> {
            Clothes saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", CLOTHES_ID);
            return saved;
        });
    }

    private Clothes existingClothes(String imageUrl) {
        return clothes(CLOTHES_ID, "원래 이름", T0, imageUrl);
    }

    private static Clothes clothes(UUID id, String name, Instant createdAt) {
        return clothes(id, name, createdAt, null);
    }

    private static Clothes clothes(UUID id, String name, Instant createdAt, String imageUrl) {
        Clothes clothes = new Clothes(OWNER_ID, name, ClothesType.TOP, imageUrl);
        ReflectionTestUtils.setField(clothes, "id", id);
        ReflectionTestUtils.setField(clothes, "createdAt", createdAt);
        return clothes;
    }

    private static ClothesAttributeDefinition definition(String name, Instant createdAt, String... values) {
        ClothesAttributeDefinition definition = new ClothesAttributeDefinition(name, List.of(values));
        ReflectionTestUtils.setField(definition, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(definition, "createdAt", createdAt);
        return definition;
    }

    private static MockMultipartFile image() {
        return new MockMultipartFile("image", "a.png", "image/png", new byte[] {1, 2, 3});
    }
}
