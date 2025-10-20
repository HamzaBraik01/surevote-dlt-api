package ma.youcode.surevote.dto.response.endpoint.publicapi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceiptExistsResponse {
    private boolean exists;
    private String uuid;
    private String message;
}

