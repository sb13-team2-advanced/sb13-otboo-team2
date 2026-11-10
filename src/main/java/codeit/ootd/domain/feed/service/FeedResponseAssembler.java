package codeit.ootd.domain.feed.service;

import codeit.ootd.domain.clothes.dto.response.ClothesAttributeWithDefDto;
import codeit.ootd.domain.clothes.entity.Clothes;
import codeit.ootd.domain.clothes.entity.ClothesAttribute;
import codeit.ootd.domain.clothes.entity.ClothesAttributeDefinition;
import codeit.ootd.domain.clothes.repository.ClothesAttributeDefinitionRepository;
import codeit.ootd.domain.clothes.repository.ClothesAttributeRepository;
import codeit.ootd.domain.clothes.repository.ClothesRepository;
import codeit.ootd.domain.feed.dto.response.AuthorDto;
import codeit.ootd.domain.feed.dto.response.FeedDto;
import codeit.ootd.domain.feed.dto.response.OotdDto;
import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.domain.feed.entity.FeedClothes;
import codeit.ootd.domain.feed.entity.FeedLike;
import codeit.ootd.domain.feed.mapper.FeedMapper;
import codeit.ootd.domain.feed.mapper.OotdMapper;
import codeit.ootd.domain.feed.repository.FeedClothesRepository;
import codeit.ootd.domain.feed.repository.FeedLikeRepository;
import codeit.ootd.domain.profile.entity.Profile;
import codeit.ootd.domain.profile.repository.ProfileRepository;
import codeit.ootd.domain.user.entity.User;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import codeit.ootd.domain.user.repository.UserRepository;
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
    private final UserRepository userRepository;
    private final ClothesRepository clothesRepository;
    private final FeedClothesRepository feedClothesRepository;
    private final FeedLikeRepository feedLikeRepository;

    @Transactional(readOnly = true)
    public List<FeedDto> toFeedDtos(List<Feed> feeds, UUID userId) {
        if (feeds.isEmpty()) {
            return List.of();
        }

        List<UUID> feedIds = feeds.stream()
                .map(Feed::getId)
                .toList();

        List<UUID> authorIds = feeds.stream()
                .map(Feed::getAuthorId)
                .distinct()
                .toList();

        Map<UUID, AuthorDto> authorsById = userRepository
                .findAllById(authorIds)
                .stream()
                .collect(Collectors.toMap(
                        User::getId,
                        this::toAuthorDto
                ));

        List<FeedClothes> feedClothes =
                feedClothesRepository.findAllByFeedIdIn(feedIds);

        Map<UUID, List<FeedClothes>> clothesLinksByFeedId =
                feedClothes.stream()
                        .sorted(Comparator.comparingInt(
                                FeedClothes::getDisplayOrder
                        ))
                        .collect(Collectors.groupingBy(
                                FeedClothes::getFeedId
                        ));

        List<UUID> clothesIds = feedClothes.stream()
                .map(FeedClothes::getClothesId)
                .distinct()
                .toList();

        Map<UUID, OotdDto> ootdsByClothesId = clothesIds.isEmpty()
                ? Map.of()
                : clothesRepository.findAllById(clothesIds)
                .stream()
                .collect(Collectors.toMap(
                        Clothes::getId,
                        this::toOotdDto
                ));

        Set<UUID> likedFeedIds = feedLikeRepository
                .findAllByUserIdAndFeedIdIn(userId, feedIds)
                .stream()
                .map(FeedLike::getFeedId)
                .collect(Collectors.toSet());

        return feeds.stream()
                .map(feed -> {
                    AuthorDto author =
                            authorsById.get(feed.getAuthorId());

                    if (author == null) {
                        throw new IllegalStateException(
                                "피드 작성자를 찾을 수 없습니다: "
                                        + feed.getAuthorId()
                        );
                    }

                    List<OotdDto> ootds = clothesLinksByFeedId
                            .getOrDefault(feed.getId(), List.of())
                            .stream()
                            .map(link -> {
                                OotdDto ootd = ootdsByClothesId.get(
                                        link.getClothesId()
                                );

                                if (ootd == null) {
                                    throw new IllegalStateException(
                                            "피드에 연결된 의상을 찾을 수 없습니다: "
                                                    + link.getClothesId()
                                    );
                                }

                                return ootd;
                            })
                            .toList();

                    return feedMapper.toListResponse(
                            feed,
                            author,
                            ootds,
                            likedFeedIds.contains(feed.getId())
                    );
                })
                .toList();
    }

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