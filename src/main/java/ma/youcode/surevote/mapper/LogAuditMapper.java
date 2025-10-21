package ma.youcode.surevote.mapper;

import ma.youcode.surevote.domain.entity.LogAudit;
import ma.youcode.surevote.dto.response.AuditLogResponse;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface LogAuditMapper {
    AuditLogResponse toResponse(LogAudit logAudit);
}

