package codeit.ootd.domain.feed.service;

import codeit.ootd.domain.clothes.dto.response.ClothesAttributeWithDefDto;
import codeit.ootd.domain.clothes.entity.Clothes;
import codeit.ootd.domain.clothes.entity.ClothesAttribute;
import codeit.ootd.domain.clothes.entity.ClothesAttributeDefinition;
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
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FeedResponseAssembler {

    private final ProfileRepository profileRepository;
    private final FeedMapper feedMapper;
    private final ClothesAttributeRepository clothesAttributeRepository;
    private final ClothesAttributeDefinitionRepository clothesAttributeDefinitionRepository;
    private final OotdMapper ootdMapper;

    public AuthorDto toAuthorDto(User author) {
        String profileImageUrl = profileRepository
                .findByUserId(author.getId())
                .map(Profile::getProfileImageUrl)
                .orElse(null);

        return feedMapper.toAuthorDto(author, profileImageUrl);
    }

    @Transactional(readOnly = true)
    public OotdDto toOotdDto(Clothes clothes) {
        List<ClothesAttribute> attributes =
                clothesAttributeRepository.findAllByClothesId(
                        clothes.getId()
                );

        if (attributes.isEmpty()) {
            return ootdMapper.toDto(clothes, List.of());
        }

        List<UUID> definitionIds = attributes.stream()
                .map(ClothesAttribute::getDefinitionId)
                .distinct()
                .toList();

        Map<UUID, ClothesAttributeDefinition> definitionsById =
                clothesAttributeDefinitionRepository
                        .findAllById(definitionIds)
                        .stream()
                        .collect(Collectors.toMap(
                                ClothesAttributeDefinition::getId,
                                Function.identity()
                        ));

        List<ClothesAttributeWithDefDto> attributeDtos =
                attributes.stream()
                        .map(attribute -> {
                            ClothesAttributeDefinition definition =
                                    definitionsById.get(
                                            attribute.getDefinitionId()
                                    );

                            if (definition == null) {
                                throw new IllegalStateException(
                                        "의상 속성 정의를 찾을 수 없습니다: "
                                                + attribute.getDefinitionId()
                                );
                            }

                            return ootdMapper.toAttributeDto(
                                    attribute,
                                    definition
                            );
                        })
                        .toList();

        return ootdMapper.toDto(clothes, attributeDtos);
    }
}