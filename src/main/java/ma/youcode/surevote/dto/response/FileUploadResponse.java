package ma.youcode.surevote.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for file upload operations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Response after successful file upload")
public class FileUploadResponse {

    @Schema(description = "The unique file identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    private String fileId;

    @Schema(description = "The original filename", example = "candidate_photo.jpg")
    private String fileName;

    @Schema(description = "The file MIME type", example = "image/jpeg")
    private String contentType;

    @Schema(description = "The file size in bytes", example = "512000")
    private long fileSize;

    @Schema(description = "The public URL to retrieve/download the file", 
            example = "http://localhost:8080/api/files/550e8400-e29b-41d4-a716-446655440000")
    private String fileUrl;

    @Schema(description = "The storage path where the file is persisted", 
            example = "/uploads/candidates/550e8400-e29b-41d4-a716-446655440000/candidate_photo.jpg")
    private String storagePath;

    @Schema(description = "The timestamp when the file was uploaded", example = "2025-03-16T10:30:45")
    private String uploadedAt;
}
