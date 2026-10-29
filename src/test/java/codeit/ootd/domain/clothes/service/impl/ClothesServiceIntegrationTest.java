package codeit.ootd.domain.clothes.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import codeit.ootd.domain.clothes.dto.request.ClothesAttributeRequest;
import codeit.ootd.domain.clothes.dto.request.ClothesCreateRequest;
import codeit.ootd.domain.clothes.dto.request.ClothesUpdateRequest;
import codeit.ootd.domain.clothes.dto.response.ClothesDto;
import codeit.ootd.domain.clothes.dto.response.ClothesDtoCursorResponse;
import codeit.ootd.domain.clothes.entity.Clothes;
import codeit.ootd.domain.clothes.entity.ClothesAttribute;
import codeit.ootd.domain.clothes.entity.ClothesAttributeDefinition;
import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.clothes.exception.ClothesInUseException;
import codeit.ootd.domain.clothes.repository.ClothesAttributeDefinitionRepository;
import codeit.ootd.domain.clothes.repository.ClothesAttributeRepository;
import codeit.ootd.domain.clothes.repository.ClothesRepository;
import codeit.ootd.domain.clothes.service.ClothesService;
import codeit.ootd.domain.feed.entity.FeedClothes;
import codeit.ootd.domain.feed.repository.FeedClothesRepository;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.infra.storage.LocalFileStorage;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 쿼리(Querydsl 커서 조회, 속성 동기화, 삭제 순서)가 H2에서 의도대로 동작하는지 검증한다.
 * 파일 저장소는 실제 빈을 감싼 스파이로 두고 업로드·삭제만 stub해서, 디스크에 파일을 만들지 않는다.
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class ClothesServiceIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    private ClothesService service;
    @Autowired
    private ClothesRepository clothesRepository;
    @Autowired
    private ClothesAttributeRepository attributeRepository;
    @Autowired
    private ClothesAttributeDefinitionRepository definitionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private FeedClothesRepository feedClothesRepository;
    @Autowired
    private EntityManager entityManager;

    @MockitoSpyBean
    private LocalFileStorage fileStorage;

    @Test
    void 등록한_의상을_조회하면_속성_정의_정보가_함께_내려온다() {
        UUID ownerId = newOwner();
        ClothesAttributeDefinition color = newDefinition("색상", "검정", "흰색");

        ClothesDto created = service.create(new ClothesCreateRequest(ownerId, "셔츠", ClothesType.TOP,
                List.of(new ClothesAttributeRequest(color.getId(), "검정"))), null);
        entityManager.flush();
        entityManager.clear();

        ClothesDtoCursorResponse response = service.list(ownerId, null, null, null, 20);

        assertThat(created.id()).isNotNull();
        assertThat(response.data()).hasSize(1);
        ClothesDto found = response.data().get(0);
        assertThat(found.id()).isEqualTo(created.id());
        assertThat(found.ownerId()).isEqualTo(ownerId);
        assertThat(found.attributes()).hasSize(1);
        assertThat(found.attributes().get(0).definitionName()).isEqualTo("색상");
        assertThat(found.attributes().get(0).selectableValues()).containsExactly("검정", "흰색");
        assertThat(found.attributes().get(0).value()).isEqualTo("검정");
        assertThat(response.totalCount()).isEqualTo(1L);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void 목록은_최신순이고_커서로_이어서_조회된다() {
        UUID ownerId = newOwner();
        Clothes oldest = saveClothes(ownerId, "c0", ClothesType.TOP, BASE);
        Clothes middle = saveClothes(ownerId, "c1", ClothesType.TOP, BASE.plusSeconds(1));
        Clothes newest = saveClothes(ownerId, "c2", ClothesType.TOP, BASE.plusSeconds(2));
        entityManager.clear();

        ClothesDtoCursorResponse first = service.list(ownerId, null, null, null, 2);

        assertThat(first.data()).extracting(ClothesDto::id).containsExactly(newest.getId(), middle.getId());
        assertThat(first.hasNext()).isTrue();
        assertThat(first.nextCursor()).isEqualTo(BASE.plusSeconds(1).toString());
        assertThat(first.nextIdAfter()).isEqualTo(middle.getId());
        assertThat(first.totalCount()).isEqualTo(3L);

        ClothesDtoCursorResponse second = service.list(ownerId, null, first.nextCursor(), first.nextIdAfter(), 2);

        assertThat(second.data()).extracting(ClothesDto::id).containsExactly(oldest.getId());
        assertThat(second.hasNext()).isFalse();
        assertThat(second.nextCursor()).isNull();
    }

    @Test
    void 생성일이_모두_같아도_커서를_따라가면_빠짐없이_중복없이_조회된다() {
        UUID ownerId = newOwner();
        Set<UUID> expected = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            expected.add(saveClothes(ownerId, "c" + i, ClothesType.TOP, BASE).getId());
        }
        entityManager.clear();

        List<UUID> collected = new ArrayList<>();
        String cursor = null;
        UUID idAfter = null;
        for (int page = 0; page < 10; page++) {
            ClothesDtoCursorResponse response = service.list(ownerId, null, cursor, idAfter, 2);
            response.data().forEach(dto -> collected.add(dto.id()));
            if (!response.hasNext()) {
                break;
            }
            cursor = response.nextCursor();
            idAfter = response.nextIdAfter();
        }

        assertThat(collected).hasSize(5).doesNotHaveDuplicates();
        assertThat(new HashSet<>(collected)).isEqualTo(expected);
    }

    @Test
    void 소유자와_타입으로_걸러서_조회하고_총_개수도_같은_조건으로_센다() {
        UUID ownerId = newOwner();
        UUID otherId = newOwner();
        Clothes top = saveClothes(ownerId, "상의", ClothesType.TOP, BASE);
        saveClothes(ownerId, "하의", ClothesType.BOTTOM, BASE.plusSeconds(1));
        saveClothes(otherId, "남의 상의", ClothesType.TOP, BASE.plusSeconds(2));
        entityManager.clear();

        ClothesDtoCursorResponse onlyTop = service.list(ownerId, ClothesType.TOP, null, null, 20);
        ClothesDtoCursorResponse all = service.list(ownerId, null, null, null, 20);

        assertThat(onlyTop.data()).extracting(ClothesDto::id).containsExactly(top.getId());
        assertThat(onlyTop.totalCount()).isEqualTo(1L);
        assertThat(all.data()).hasSize(2);
        assertThat(all.totalCount()).isEqualTo(2L);
    }

    @Test
    void 수정하면_속성이_값변경_삭제_추가로_맞춰지고_유니크_제약에_걸리지_않는다() {
        UUID ownerId = newOwner();
        ClothesAttributeDefinition color = newDefinition("색상", "검정", "흰색");
        ClothesAttributeDefinition size = newDefinition("사이즈", "S", "M");
        ClothesAttributeDefinition material = newDefinition("소재", "면", "울");
        ClothesDto created = service.create(new ClothesCreateRequest(ownerId, "셔츠", ClothesType.TOP, List.of(
                new ClothesAttributeRequest(color.getId(), "검정"),
                new ClothesAttributeRequest(size.getId(), "M"))), null);
        entityManager.flush();
        entityManager.clear();

        ClothesDto updated = service.update(created.id(), new ClothesUpdateRequest("긴팔 셔츠", ClothesType.OUTER,
                List.of(new ClothesAttributeRequest(color.getId(), "흰색"),
                        new ClothesAttributeRequest(material.getId(), "면"))), null);
        entityManager.flush();
        entityManager.clear();

        List<ClothesAttribute> saved = attributeRepository.findAllByClothesId(created.id());
        assertThat(saved).extracting(ClothesAttribute::getDefinitionId)
                .containsExactlyInAnyOrder(color.getId(), material.getId());
        assertThat(saved).extracting(ClothesAttribute::getValue).containsExactlyInAnyOrder("흰색", "면");
        assertThat(updated.name()).isEqualTo("긴팔 셔츠");
        assertThat(updated.type()).isEqualTo(ClothesType.OUTER);
        assertThat(updated.attributes()).extracting(a -> a.definitionName()).containsExactly("색상", "소재");
    }

    @Test
    void 속성을_보내지_않고_수정하면_기존_속성이_유지된다() {
        UUID ownerId = newOwner();
        ClothesAttributeDefinition color = newDefinition("색상", "검정", "흰색");
        ClothesDto created = service.create(new ClothesCreateRequest(ownerId, "셔츠", ClothesType.TOP,
                List.of(new ClothesAttributeRequest(color.getId(), "검정"))), null);
        entityManager.flush();
        entityManager.clear();

        ClothesDto updated = service.update(created.id(), new ClothesUpdateRequest("새 이름", null, null), null);

        assertThat(updated.name()).isEqualTo("새 이름");
        assertThat(updated.attributes()).hasSize(1);
        assertThat(updated.attributes().get(0).value()).isEqualTo("검정");
    }

    @Test
    void 삭제하면_의상과_속성이_함께_사라진다() {
        UUID ownerId = newOwner();
        ClothesAttributeDefinition color = newDefinition("색상", "검정", "흰색");
        ClothesDto created = service.create(new ClothesCreateRequest(ownerId, "셔츠", ClothesType.TOP,
                List.of(new ClothesAttributeRequest(color.getId(), "검정"))), null);
        entityManager.flush();
        entityManager.clear();

        service.delete(created.id());
        entityManager.flush();
        entityManager.clear();

        assertThat(clothesRepository.findById(created.id())).isEmpty();
        assertThat(attributeRepository.findAllByClothesId(created.id())).isEmpty();
    }

    // ---------- 이미지 파일은 트랜잭션 결과에 맞춰 정리된다 ----------

    @Test
    void 등록이_롤백되면_업로드한_이미지를_삭제한다() {
        UUID ownerId = newOwner();
        stubStorage("/files/clothes/new.png");

        service.create(new ClothesCreateRequest(ownerId, "셔츠", ClothesType.TOP, null), image());
        verify(fileStorage, never()).delete(any());

        TestTransaction.flagForRollback();
        TestTransaction.end();

        verify(fileStorage).delete("/files/clothes/new.png");
    }

    @Test
    void 수정이_롤백되면_새_이미지만_삭제하고_이전_이미지는_남긴다() {
        UUID ownerId = newOwner();
        Clothes clothes = clothesRepository.saveAndFlush(
                new Clothes(ownerId, "셔츠", ClothesType.TOP, "/files/clothes/old.png"));
        stubStorage("/files/clothes/new.png");

        service.update(clothes.getId(), new ClothesUpdateRequest(null, null, null), image());
        verify(fileStorage, never()).delete(any());

        TestTransaction.flagForRollback();
        TestTransaction.end();

        verify(fileStorage).delete("/files/clothes/new.png");
        verify(fileStorage, never()).delete("/files/clothes/old.png");
    }

    @Test
    void 수정이_커밋되면_이전_이미지를_삭제하고_새_이미지는_남긴다() {
        UUID ownerId = newOwner();
        Clothes clothes = clothesRepository.saveAndFlush(
                new Clothes(ownerId, "셔츠", ClothesType.TOP, "/files/clothes/old.png"));
        stubStorage("/files/clothes/new.png");

        service.update(clothes.getId(), new ClothesUpdateRequest(null, null, null), image());
        verify(fileStorage, never()).delete(any());

        TestTransaction.flagForCommit();
        TestTransaction.end();
        try {
            verify(fileStorage).delete("/files/clothes/old.png");
            verify(fileStorage, never()).delete("/files/clothes/new.png");
        } finally {
            cleanUp(clothes.getId(), ownerId);
        }
    }

    @Test
    void 삭제가_롤백되면_이미지를_삭제하지_않는다() {
        UUID ownerId = newOwner();
        Clothes clothes = clothesRepository.saveAndFlush(
                new Clothes(ownerId, "셔츠", ClothesType.TOP, "/files/clothes/old.png"));
        stubStorage("/files/clothes/new.png");

        service.delete(clothes.getId());
        TestTransaction.flagForRollback();
        TestTransaction.end();

        verify(fileStorage, never()).delete(any());
    }

    @Test
    void 삭제가_커밋되면_이미지를_삭제한다() {
        UUID ownerId = newOwner();
        Clothes clothes = clothesRepository.saveAndFlush(
                new Clothes(ownerId, "셔츠", ClothesType.TOP, "/files/clothes/old.png"));
        stubStorage("/files/clothes/new.png");

        service.delete(clothes.getId());
        verify(fileStorage, never()).delete(any());

        TestTransaction.flagForCommit();
        TestTransaction.end();
        try {
            verify(fileStorage).delete("/files/clothes/old.png");
        } finally {
            cleanUp(null, ownerId);
        }
    }

    @Test
    void 피드에서_사용_중인_의상은_삭제할_수_없고_그대로_남는다() {
        UUID ownerId = newOwner();
        Clothes clothes = saveClothes(ownerId, "셔츠", ClothesType.TOP, BASE);
        feedClothesRepository.saveAndFlush(new FeedClothes(UUID.randomUUID(), clothes.getId(), 0));
        entityManager.clear();

        assertThat(clothesRepository.existsInFeed(clothes.getId())).isTrue();
        assertThatThrownBy(() -> service.delete(clothes.getId())).isInstanceOf(ClothesInUseException.class);

        assertThat(clothesRepository.findById(clothes.getId())).isPresent();
    }

    // ---------- 준비 ----------

    private void stubStorage(String uploadedUrl) {
        doReturn(uploadedUrl).when(fileStorage).upload(any(), eq("clothes"));
        doNothing().when(fileStorage).delete(any());
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("image", "a.png", "image/png", new byte[] {1});
    }

    /**
     * 커밋까지 진행한 테스트가 남긴 데이터를 새 트랜잭션에서 지운다.
     */
    private void cleanUp(UUID clothesId, UUID ownerId) {
        TestTransaction.start();
        if (clothesId != null) {
            clothesRepository.deleteById(clothesId);
        }
        userRepository.deleteById(ownerId);
        TestTransaction.flagForCommit();
        TestTransaction.end();
    }

    private UUID newOwner() {
        return userRepository.saveAndFlush(
                User.create("테스터", UUID.randomUUID() + "@test.com", "encoded")).getId();
    }

    private ClothesAttributeDefinition newDefinition(String name, String... values) {
        return definitionRepository.saveAndFlush(new ClothesAttributeDefinition(name, List.of(values)));
    }

    /**
     * 생성일은 수정할 수 없는 컬럼이라 저장 직후 쿼리로 원하는 값으로 맞춘다.
     */
    private Clothes saveClothes(UUID ownerId, String name, ClothesType type, Instant createdAt) {
        Clothes saved = clothesRepository.saveAndFlush(new Clothes(ownerId, name, type, null));
        entityManager.createNativeQuery("update clothes set created_at = ?1 where id = ?2")
                .setParameter(1, createdAt)
                .setParameter(2, saved.getId())
                .executeUpdate();
        return saved;
    }
}
