package ma.youcode.surevote.dto.response.endpoint.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollegeMemberCountResponse {
    private Long collegeId;
    private long memberCount;
}

