package ma.youcode.surevote.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.youcode.surevote.dto.response.FileUploadResponse;
import ma.youcode.surevote.service.FileStorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * REST controller for file upload and download operations.
 *
 * Admin-only endpoints:
 *   POST /api/admin/files/upload/photo/{candidatId}  — Upload candidate photo
 *   POST /api/admin/files/upload/program/{candidatId} — Upload candidate program PDF
 *   POST /api/admin/files/upload/election/{electionId} — Upload election document
 *   DELETE /api/admin/files/{fileId} — Delete a file
 *
 * Public endpoint:
 *   GET /api/files/download/{path} — Download a file (public access)
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "File Management",
    description = "File upload and download operations for candidates, elections, and documents"
)
public class FileUploadController {

    private final FileStorageService fileStorageService;

    // =========================================================
    // File Upload Operations (Admin Only)
    // =========================================================

    /**
     * Uploads a candidate's profile photo.
     *
     * Allowed formats: JPEG, PNG, WebP
     * Maximum size: 5 MB
     *
     * @param candidatId the candidate's ID
     * @param file the multipart photo file
     * @return FileUploadResponse with the file URL
     */
    @PostMapping("/api/admin/files/upload/photo/{candidatId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Upload candidate profile photo",
        description = "Uploads a JPEG, PNG, or WebP photo for a candidate (max 5 MB)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "File uploaded successfully",
            content = @Content(schema = @Schema(implementation = FileUploadResponse.class))),
        @ApiResponse(responseCode = "400", description = "File validation failed"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (ADMIN role required)")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<FileUploadResponse> uploadCandidatePhoto(
            @PathVariable Long candidatId,
            @RequestParam("file") MultipartFile file) throws IOException {
        log.info("Uploading candidate photo: candidatId={}, fileName={}", candidatId, file.getOriginalFilename());
        FileUploadResponse response = fileStorageService.uploadCandidatePhoto(candidatId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Uploads a candidate's program document (PDF).
     *
     * Allowed format: PDF
     * Maximum size: 10 MB
     *
     * @param candidatId the candidate's ID
     * @param file the multipart PDF file
     * @return FileUploadResponse with the file URL
     */
    @PostMapping("/api/admin/files/upload/program/{candidatId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Upload candidate program PDF",
        description = "Uploads a PDF document with the candidate's program (max 10 MB)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "File uploaded successfully",
            content = @Content(schema = @Schema(implementation = FileUploadResponse.class))),
        @ApiResponse(responseCode = "400", description = "File is not a valid PDF"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions (ADMIN role required)")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<FileUploadResponse> uploadCandidateProgram(
            @PathVariable Long candidatId,
            @RequestParam("file") MultipartFile file) throws IOException {
        log.info("Uploading candidate program: candidatId={}, fileName={}", candidatId, file.getOriginalFilename());
        FileUploadResponse response = fileStorageService.uploadCandidateProgramPdf(candidatId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Uploads an election document (banner, rules, etc.).
     *
     * Allowed formats: JPEG, PNG, WebP, PDF
     * Maximum size: 10 MB
     *
     * @param electionId the election's ID
     * @param file the multipart file
     * @return FileUploadResponse with the file URL
     */
    @PostMapping("/api/admin/files/upload/election/{electionId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Upload election document",
        description = "Uploads a document related to an election (banner, rules, etc.)"
    )
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<FileUploadResponse> uploadElectionFile(
            @PathVariable Long electionId,
            @RequestParam("file") MultipartFile file) throws IOException {
        log.info("Uploading election file: electionId={}, fileName={}", electionId, file.getOriginalFilename());
        FileUploadResponse response = fileStorageService.uploadElectionFile(electionId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Deletes a previously uploaded file.
     *
     * @param filePath the storage path of the file to delete
     * @return 204 No Content on success
     */
    @DeleteMapping("/api/admin/files")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Delete an uploaded file",
        description = "Removes a file from storage by its storage path"
    )
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Void> deleteFile(@RequestParam String filePath) throws IOException {
        log.info("Deleting file: {}", filePath);
        fileStorageService.deleteFile(filePath);
        return ResponseEntity.noContent().build();
    }

    // =========================================================
    // File Download (Public Access)
    // =========================================================

    /**
     * Downloads a file by its storage path (public access).
     *
     * Example URL: /api/files/download/candidates/123/abc-def-456.jpg
     *
     * @param path the file's storage path
     * @return the file content as an attachment
     */
    @GetMapping("/api/files/download/**")
    @Operation(
        summary = "Download a file",
        description = "Retrieves a file for download (available to all authenticated users)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File downloaded successfully"),
        @ApiResponse(responseCode = "404", description = "File not found")
    })
    public ResponseEntity<byte[]> downloadFile(HttpServletRequest request) throws IOException {
        // Extract the file path from the request URL
        String requestPath = request.getRequestURI();
        String basePath = "/api/files/download/";
        if (!requestPath.startsWith(basePath)) {
            return ResponseEntity.notFound().build();
        }

        String filePath = requestPath.substring(basePath.length());
        log.info("Downloading file: {}", filePath);

        try {
            byte[] fileContent = fileStorageService.getFileContent(filePath);

            // Determine content type from file extension
            String contentType = getContentType(filePath);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                           "attachment; filename=\"" + getFileName(filePath) + "\"")
                    .body(fileContent);
        } catch (IOException e) {
            log.warn("File not found or error reading: {}", filePath);
            return ResponseEntity.notFound().build();
        }
    }

    // ========================================================
    // Helper Methods
    // =========================================================

    /**
     * Determines MIME type from file extension.
     */
    private String getContentType(String filePath) {
        if (filePath.endsWith(".jpg") || filePath.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG_VALUE;
        } else if (filePath.endsWith(".png")) {
            return MediaType.IMAGE_PNG_VALUE;
        } else if (filePath.endsWith(".webp")) {
            return "image/webp";
        } else if (filePath.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF_VALUE;
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    /**
     * Extracts filename from storage path.
     */
    private String getFileName(String filePath) {
        String[] parts = filePath.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : "download";
    }
}
