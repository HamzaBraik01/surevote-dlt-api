package ma.youcode.surevote.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.youcode.surevote.annotation.Auditable;
import ma.youcode.surevote.dto.response.FileUploadResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Service responsible for file storage and retrieval.
 *
 * Features:
 *  - Upload candidate photos and program PDFs
 *  - Organize files by candidate in subdirectories
 *  - Generate secure, publicly accessible file URLs
 *  - Delete files with cleanup
 *  - Enforce file type and size limits
 *
 * Storage structure:
 *  /uploads/candidates/{candidatId}/{fileName}
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${app.api-url:http://localhost:8080}")
    private String apiBaseUrl;

    // Allowed MIME types for candidate files
    private static final String[] ALLOWED_PHOTO_TYPES = {"image/jpeg", "image/png", "image/webp"};
    private static final String[] ALLOWED_PDF_TYPES = {"application/pdf"};
    private static final long MAX_PHOTO_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final long MAX_PDF_SIZE = 10 * 1024 * 1024; // 10 MB

    // =========================================================
    // File Upload Operations
    // =========================================================

    /**
     * Uploads a candidate profile photo.
     *
     * Validations:
     *  - File must be JPEG, PNG, or WebP
     *  - File size must not exceed 5 MB
     *  - File is stored with a UUID filename
     *
     * @param candidatId the candidate's ID (used for directory organization)
     * @param file the multipart file to upload
     * @return FileUploadResponse with the file's public URL
     * @throws IOException if the file cannot be written
     * @throws IllegalArgumentException if the file is invalid
     */
    @Auditable(actionType = "FILE_UPLOADED", description = "Fichier téléchargé avec succès")
    public FileUploadResponse uploadCandidatePhoto(Long candidatId, MultipartFile file) throws IOException {
        validateFile(file, ALLOWED_PHOTO_TYPES, MAX_PHOTO_SIZE);
        return storeFile(file, "candidates", candidatId);
    }

    /**
     * Uploads a candidate program (PDF document).
     *
     * Validations:
     *  - File must be a PDF
     *  - File size must not exceed 10 MB
     *  - File is stored with a UUID filename
     *
     * @param candidatId the candidate's ID (used for directory organization)
     * @param file the multipart PDF file to upload
     * @return FileUploadResponse with the file's public URL
     * @throws IOException if the file cannot be written
     * @throws IllegalArgumentException if the file is not a valid PDF
     */
    @Auditable(actionType = "FILE_UPLOADED", description = "Programme candidat téléchargé")
    public FileUploadResponse uploadCandidateProgramPdf(Long candidatId, MultipartFile file) throws IOException {
        validateFile(file, ALLOWED_PDF_TYPES, MAX_PDF_SIZE);
        return storeFile(file, "programs", candidatId);
    }

    /**
     * Uploads an election banner or document (flexible MIME types).
     *
     * @param electionId the election's ID
     * @param file the file to upload
     * @return FileUploadResponse with the file's public URL
     * @throws IOException if the file cannot be written
     */
    @Auditable(actionType = "FILE_UPLOADED", description = "Fichier élection téléchargé")
    public FileUploadResponse uploadElectionFile(Long electionId, MultipartFile file) throws IOException {
        // For election files, allow photos + PDFs
        String[] allowedTypes = new String[ALLOWED_PHOTO_TYPES.length + ALLOWED_PDF_TYPES.length];
        System.arraycopy(ALLOWED_PHOTO_TYPES, 0, allowedTypes, 0, ALLOWED_PHOTO_TYPES.length);
        System.arraycopy(ALLOWED_PDF_TYPES, 0, allowedTypes, ALLOWED_PHOTO_TYPES.length, ALLOWED_PDF_TYPES.length);

        validateFile(file, allowedTypes, MAX_PDF_SIZE);
        return storeFile(file, "elections", electionId);
    }

    // =========================================================
    // File Deletion
    // =========================================================

    /**
     * Deletes a file from storage by its storage path.
     *
     * @param storagePath the relative path to the file (e.g., /candidates/123/abc-def.jpg)
     * @throws IOException if the file cannot be deleted
     */
    @Auditable(actionType = "FILE_DELETED", description = "Fichier supprimé")
    public void deleteFile(String storagePath) throws IOException {
        Path fullPath = Paths.get(uploadDir, storagePath);

        if (!Files.exists(fullPath)) {
            log.warn("File not found for deletion: {}", fullPath);
            return;
        }

        try {
            Files.delete(fullPath);
            log.info("File deleted: {}", fullPath);
        } catch (IOException e) {
            log.error("Failed to delete file: {}", fullPath, e);
            throw e;
        }
    }

    // =========================================================
    // File Retrieval
    // =========================================================

    /**
     * Generates a public download URL for a file.
     *
     * @param storagePath the relative path to the file
     * @return a publicly accessible URL
     */
    public String generateFileUrl(String storagePath) {
        return apiBaseUrl + "/api/files/download" + storagePath;
    }

    /**
     * Retrieves file content as bytes.
     *
     * @param storagePath the relative path to the file
     * @return the file contents as a byte array
     * @throws IOException if the file cannot be read
     */
    public byte[] getFileContent(String storagePath) throws IOException {
        Path fullPath = Paths.get(uploadDir, storagePath);

        if (!Files.exists(fullPath)) {
            throw new IOException("File not found: " + fullPath);
        }

        return Files.readAllBytes(fullPath);
    }

    // =========================================================
    // Helper Methods
    // =========================================================

    /**
     * Stores a file in the appropriate directory and returns a response DTO.
     */
    private FileUploadResponse storeFile(MultipartFile file, String category, Long entityId) throws IOException {
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        String fileExtension = getFileExtension(originalFileName);

        // Generate unique filename with UUID
        String uniqueFileName = UUID.randomUUID() + "." + fileExtension;

        // Create directory structure: /uploads/category/entityId/
        Path directory = Paths.get(uploadDir, category, entityId.toString());
        Files.createDirectories(directory);

        // Store file
        Path filePath = directory.resolve(uniqueFileName);
        Files.write(filePath, file.getBytes());

        log.info("File stored successfully: {}", filePath);

        // Build response
        String relativePath = category + "/" + entityId + "/" + uniqueFileName;
        String fileUrl = generateFileUrl(relativePath);

        return FileUploadResponse.builder()
                .fileId(UUID.randomUUID().toString())
                .fileName(originalFileName)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .fileUrl(fileUrl)
                .storagePath(relativePath)
                .uploadedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();
    }

    /**
     * Validates file type and size.
     */
    private void validateFile(MultipartFile file, String[] allowedTypes, long maxSize) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier ne peut pas être vide");
        }

        String contentType = file.getContentType();
        if (contentType == null || !isAllowedType(contentType, allowedTypes)) {
            throw new IllegalArgumentException(
                    "Type de fichier non autorisé: " + contentType + ". Types acceptés: " + String.join(", ", allowedTypes)
            );
        }

        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException(
                    "Le fichier dépasse la taille maximale autorisée (" + (maxSize / 1024 / 1024) + " MB)"
            );
        }
    }

    /**
     * Checks if a MIME type is in the allowed list.
     */
    private boolean isAllowedType(String contentType, String[] allowedTypes) {
        for (String allowed : allowedTypes) {
            if (contentType.equalsIgnoreCase(allowed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts file extension from filename.
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "bin";
        }
        int lastDot = fileName.lastIndexOf(".");
        if (lastDot <= 0) {
            return "bin";
        }
        return fileName.substring(lastDot + 1).toLowerCase();
    }
}

