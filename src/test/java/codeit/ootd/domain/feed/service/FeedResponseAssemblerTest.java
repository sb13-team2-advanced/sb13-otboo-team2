package codeit.ootd.domain.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.clothes.dto.response.ClothesAttributeWithDefDto;
import codeit.ootd.domain.clothes.entity.Clothes;
import codeit.ootd.domain.clothes.entity.ClothesAttribute;
import codeit.ootd.domain.clothes.entity.ClothesAttributeDefinition;
import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.clothes.repository.ClothesAttributeDefinitionRepository;
import codeit.ootd.domain.clothes.repository.ClothesAttributeRepository;
import codeit.ootd.domain.feed.dto.response.AuthorDto;
import codeit.ootd.domain.feed.dto.response.OotdDto;
import codeit.ootd.domain.feed.mapper.FeedMapper;
import codeit.ootd.domain.feed.mapper.OotdMapper;
import codeit.ootd.domain.profile.entity.Profile;
import codeit.ootd.domain.profile.repository.ProfileRepository;
import codeit.ootd.domain.user.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("피드 응답 구성 테스트")
class FeedResponseAssemblerTest {

    private final ProfileRepository profileRepository =
            mock(ProfileRepository.class);

    private final FeedMapper feedMapper =
            Mappers.getMapper(FeedMapper.class);

    private final ClothesAttributeRepository clothesAttributeRepository =
            mock(ClothesAttributeRepository.class);

    private final ClothesAttributeDefinitionRepository clothesAttributeDefinitionRepository =
            mock(ClothesAttributeDefinitionRepository.class);

    private final OotdMapper ootdMapper =
            Mappers.getMapper(OotdMapper.class);

    private final FeedResponseAssembler assembler =
            new FeedResponseAssembler(
                    profileRepository,
                    feedMapper,
                    clothesAttributeRepository,
                    clothesAttributeDefinitionRepository,
                    ootdMapper
            );

    @Test
    @DisplayName("작성자 정보와 프로필 이미지를 함께 반환한다")
    void createsAuthorResponseWithProfileImage() {
        UUID authorId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        String imageUrl = "/images/profile.png";

        User author = createAuthor(authorId);
        Profile profile = new Profile(authorId);

        ReflectionTestUtils.setField(profile, "id", profileId);
        ReflectionTestUtils.setField(
                profile,
                "profileImageUrl",
                imageUrl
        );

        when(profileRepository.findByUserId(authorId))
                .thenReturn(Optional.of(profile));

        AuthorDto result = assembler.toAuthorDto(author);

        assertThat(result.userId()).isEqualTo(authorId);
        assertThat(result.name()).isEqualTo("작성자");
        assertThat(result.profileImageUrl()).isEqualTo(imageUrl);

        verify(profileRepository).findByUserId(authorId);
    }

    @Test
    @DisplayName("프로필 이미지가 없으면 이미지 URL은 null로 반환한다")
    void preservesNullWhenProfileImageIsMissing() {
        UUID authorId = UUID.randomUUID();
        User author = createAuthor(authorId);
        Profile profile = new Profile(authorId);

        when(profileRepository.findByUserId(authorId))
                .thenReturn(Optional.of(profile));

        AuthorDto result = assembler.toAuthorDto(author);

        assertThat(result.userId()).isEqualTo(authorId);
        assertThat(result.name()).isEqualTo("작성자");
        assertThat(result.profileImageUrl()).isNull();

        verify(profileRepository).findByUserId(authorId);
    }

    @Test
    @DisplayName("프로필이 없어도 작성자 정보는 유지한다")
    void createsAuthorResponseWhenProfileIsMissing() {
        UUID authorId = UUID.randomUUID();
        User author = createAuthor(authorId);

        when(profileRepository.findByUserId(authorId))
                .thenReturn(Optional.empty());

        AuthorDto result = assembler.toAuthorDto(author);

        assertThat(result.userId()).isEqualTo(authorId);
        assertThat(result.name()).isEqualTo("작성자");
        assertThat(result.profileImageUrl()).isNull();

        verify(profileRepository).findByUserId(authorId);
    }

    @Test
    @DisplayName("속성 정의의 조회 순서가 달라도 ID로 연결해 의상 응답을 구성한다")
    void createsOotdResponseByMatchingDefinitionIds() {
        UUID clothesId = UUID.randomUUID();
        UUID colorDefinitionId = UUID.randomUUID();
        UUID materialDefinitionId = UUID.randomUUID();

        Clothes clothes = new Clothes(
                UUID.randomUUID(),
                "흰색 티셔츠",
                ClothesType.TOP,
                "/images/tshirt.png"
        );
        ReflectionTestUtils.setField(clothes, "id", clothesId);

        ClothesAttribute color = new ClothesAttribute(
                clothesId,
                colorDefinitionId,
                "흰색"
        );
        ClothesAttribute material = new ClothesAttribute(
                clothesId,
                materialDefinitionId,
                "면"
        );

        ClothesAttributeDefinition colorDefinition =
                new ClothesAttributeDefinition(
                        "색상",
                        List.of("흰색", "검정")
                );
        ReflectionTestUtils.setField(
                colorDefinition,
                "id",
                colorDefinitionId
        );

        ClothesAttributeDefinition materialDefinition =
                new ClothesAttributeDefinition(
                        "소재",
                        List.of("면", "울")
                );
        ReflectionTestUtils.setField(
                materialDefinition,
                "id",
                materialDefinitionId
        );

        when(clothesAttributeRepository.findAllByClothesId(clothesId))
                .thenReturn(List.of(color, material));

        when(clothesAttributeDefinitionRepository.findAllById(
                List.of(colorDefinitionId, materialDefinitionId)
        )).thenReturn(List.of(materialDefinition, colorDefinition));

        OotdDto result = assembler.toOotdDto(clothes);

        assertThat(result.clothesId()).isEqualTo(clothesId);
        assertThat(result.name()).isEqualTo("흰색 티셔츠");
        assertThat(result.imageUrl()).isEqualTo("/images/tshirt.png");
        assertThat(result.type()).isEqualTo("TOP");
        assertThat(result.attributes()).containsExactlyInAnyOrder(
                new ClothesAttributeWithDefDto(
                        colorDefinitionId,
                        "색상",
                        List.of("흰색", "검정"),
                        "흰색"
                ),
                new ClothesAttributeWithDefDto(
                        materialDefinitionId,
                        "소재",
                        List.of("면", "울"),
                        "면"
                )
        );

        verify(clothesAttributeRepository)
                .findAllByClothesId(clothesId);
        verify(clothesAttributeDefinitionRepository)
                .findAllById(
                        List.of(colorDefinitionId, materialDefinitionId)
                );
    }

    @Test
    @DisplayName("등록된 속성이 없으면 의상 정보와 빈 속성 목록을 반환한다")
    void createsOotdResponseWhenAttributesAreEmpty() {
        UUID clothesId = UUID.randomUUID();

        Clothes clothes = new Clothes(
                UUID.randomUUID(),
                "청바지",
                ClothesType.BOTTOM,
                "/images/jeans.png"
        );
        ReflectionTestUtils.setField(clothes, "id", clothesId);

        when(clothesAttributeRepository.findAllByClothesId(clothesId))
                .thenReturn(List.of());

        OotdDto result = assembler.toOotdDto(clothes);

        assertThat(result).isEqualTo(
                new OotdDto(
                        clothesId,
                        "청바지",
                        "/images/jeans.png",
                        "BOTTOM",
                        List.of()
                )
        );

        verify(clothesAttributeRepository)
                .findAllByClothesId(clothesId);
        verifyNoInteractions(clothesAttributeDefinitionRepository);
    }

    @Test
    @DisplayName("의상 속성이 참조하는 정의가 없으면 예외를 발생시킨다")
    void rejectsOotdResponseWhenAttributeDefinitionIsMissing() {
        UUID clothesId = UUID.randomUUID();
        UUID missingDefinitionId = UUID.randomUUID();

        Clothes clothes = new Clothes(
                UUID.randomUUID(),
                "흰색 티셔츠",
                ClothesType.TOP,
                "/images/tshirt.png"
        );
        ReflectionTestUtils.setField(clothes, "id", clothesId);

        ClothesAttribute attribute = new ClothesAttribute(
                clothesId,
                missingDefinitionId,
                "흰색"
        );

        when(clothesAttributeRepository.findAllByClothesId(clothesId))
                .thenReturn(List.of(attribute));
        when(clothesAttributeDefinitionRepository.findAllById(
                List.of(missingDefinitionId)
        )).thenReturn(List.of());

        assertThatThrownBy(() -> assembler.toOotdDto(clothes))
                .isInstanceOf(IllegalStateException.class);

        verify(clothesAttributeRepository)
                .findAllByClothesId(clothesId);
        verify(clothesAttributeDefinitionRepository)
                .findAllById(List.of(missingDefinitionId));
    }

    private User createAuthor(UUID authorId) {
        User author = User.create(
                "작성자",
                "author@example.com",
                "encoded-password"
        );

        ReflectionTestUtils.setField(author, "id", authorId);

        return author;
    }
}