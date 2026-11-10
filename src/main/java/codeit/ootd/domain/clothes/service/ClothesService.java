package codeit.ootd.domain.clothes.service;

import codeit.ootd.domain.clothes.dto.request.ClothesCreateRequest;
import codeit.ootd.domain.clothes.dto.request.ClothesUpdateRequest;
import codeit.ootd.domain.clothes.dto.response.ClothesDto;
import codeit.ootd.domain.clothes.dto.response.ClothesDtoCursorResponse;
import codeit.ootd.domain.clothes.enums.ClothesType;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface ClothesService {

    ClothesDto create(ClothesCreateRequest request, MultipartFile image);

    ClothesDtoCursorResponse list(UUID ownerId, ClothesType typeEqual, String cursor, UUID idAfter, int limit);

    ClothesDto update(UUID clothesId, ClothesUpdateRequest request, MultipartFile image);

    void delete(UUID clothesId);
}
