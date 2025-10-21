package ma.youcode.surevote.mapper.endpoint;

import ma.youcode.surevote.dto.response.endpoint.publicapi.ReceiptExistsResponse;
import ma.youcode.surevote.mapper.MapStructConfig;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface PublicEndpointMapper {
    ReceiptExistsResponse toReceiptExistsResponse(boolean exists, String uuid, String message);
}

