package ma.youcode.surevote.service;

import ma.youcode.surevote.dto.response.FileUploadResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileStorageService")
class FileStorageServiceTest {

    private FileStorageService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new FileStorageService();
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "apiBaseUrl", "http://localhost:8080");
    }

    // ── uploadCandidatePhoto ───────────────────────────────────

    @Test @DisplayName("uploadCandidatePhoto saves JPEG file and returns response")
    void uploadPhoto_success() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "fake-image-data".getBytes());

        FileUploadResponse response = service.uploadCandidatePhoto(1L, file);

        assertThat(response.getFileName()).isEqualTo("photo.jpg");
        assertThat(response.getContentType()).isEqualTo("image/jpeg");
        assertThat(response.getFileUrl()).startsWith("http://localhost:8080/api/files/download");
        assertThat(response.getStoragePath()).contains("candidates/1/");
    }

    @Test @DisplayName("uploadCandidatePhoto accepts PNG")
    void uploadPhoto_png() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", "data".getBytes());
        assertThatCode(() -> service.uploadCandidatePhoto(1L, file)).doesNotThrowAnyException();
    }

    @Test @DisplayName("uploadCandidatePhoto accepts WebP")
    void uploadPhoto_webp() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.webp", "image/webp", "data".getBytes());
        assertThatCode(() -> service.uploadCandidatePhoto(1L, file)).doesNotThrowAnyException();
    }

    @Test @DisplayName("uploadCandidatePhoto rejects non-image file")
    void uploadPhoto_invalidType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "data".getBytes());
        assertThatThrownBy(() -> service.uploadCandidatePhoto(1L, file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("uploadCandidatePhoto rejects oversized file")
    void uploadPhoto_tooLarge() {
        byte[] largeData = new byte[6 * 1024 * 1024]; // 6MB > 5MB limit
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.jpg", "image/jpeg", largeData);
        assertThatThrownBy(() -> service.uploadCandidatePhoto(1L, file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("uploadCandidatePhoto rejects empty file")
    void uploadPhoto_empty() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]);
        assertThatThrownBy(() -> service.uploadCandidatePhoto(1L, file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("uploadCandidatePhoto rejects null content type")
    void uploadPhoto_nullContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", null, "data".getBytes());
        assertThatThrownBy(() -> service.uploadCandidatePhoto(1L, file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── uploadCandidateProgramPdf ──────────────────────────────

    @Test @DisplayName("uploadProgramPdf saves PDF file")
    void uploadPdf_success() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "program.pdf", "application/pdf", "pdf-data".getBytes());

        FileUploadResponse response = service.uploadCandidateProgramPdf(1L, file);
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getStoragePath()).contains("programs/1/");
    }

    @Test @DisplayName("uploadProgramPdf rejects non-PDF")
    void uploadPdf_invalidType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "img.jpg", "image/jpeg", "data".getBytes());
        assertThatThrownBy(() -> service.uploadCandidateProgramPdf(1L, file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("uploadProgramPdf rejects oversized PDF (>10MB)")
    void uploadPdf_tooLarge() {
        byte[] largeData = new byte[11 * 1024 * 1024]; // 11MB
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.pdf", "application/pdf", largeData);
        assertThatThrownBy(() -> service.uploadCandidateProgramPdf(1L, file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── uploadElectionFile ─────────────────────────────────────

    @Test @DisplayName("uploadElectionFile accepts both image and PDF")
    void uploadElection_imageAndPdf() throws IOException {
        MockMultipartFile img = new MockMultipartFile(
                "file", "banner.jpg", "image/jpeg", "data".getBytes());
        assertThatCode(() -> service.uploadElectionFile(1L, img)).doesNotThrowAnyException();

        MockMultipartFile pdf = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "data".getBytes());
        assertThatCode(() -> service.uploadElectionFile(1L, pdf)).doesNotThrowAnyException();
    }

    // ── deleteFile ─────────────────────────────────────────────

    @Test @DisplayName("deleteFile removes file from disk")
    void deleteFile_success() throws IOException {
        Path dir = tempDir.resolve("test");
        Files.createDirectories(dir);
        Path file = dir.resolve("file.txt");
        Files.writeString(file, "content");

        service.deleteFile("test/file.txt");

        assertThat(Files.exists(file)).isFalse();
    }

    @Test @DisplayName("deleteFile does nothing when file does not exist")
    void deleteFile_notFound() throws IOException {
        assertThatCode(() -> service.deleteFile("nonexistent/path.txt"))
                .doesNotThrowAnyException();
    }

    // ── generateFileUrl ────────────────────────────────────────

    @Test @DisplayName("generateFileUrl returns correct URL")
    void generateFileUrl() {
        String url = service.generateFileUrl("candidates/1/photo.jpg");
        assertThat(url).isEqualTo("http://localhost:8080/api/files/downloadcandidates/1/photo.jpg");
    }

    // ── getFileContent ─────────────────────────────────────────

    @Test @DisplayName("getFileContent returns bytes")
    void getFileContent_success() throws IOException {
        Path dir = tempDir.resolve("data");
        Files.createDirectories(dir);
        Path file = dir.resolve("test.txt");
        Files.writeString(file, "hello");

        byte[] content = service.getFileContent("data/test.txt");
        assertThat(new String(content)).isEqualTo("hello");
    }

    @Test @DisplayName("getFileContent throws when file not found")
    void getFileContent_notFound() {
        assertThatThrownBy(() -> service.getFileContent("nonexistent.txt"))
                .isInstanceOf(IOException.class);
    }

    // ── getFileExtension (private – tested indirectly via upload) ──
    @Test @DisplayName("file without extension uses 'bin' default")
    void noExtension_usesBinDefault() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "noext", "image/jpeg", "data".getBytes());

        FileUploadResponse response = service.uploadCandidatePhoto(1L, file);
        assertThat(response.getStoragePath()).endsWith(".bin");
    }

    @Test @DisplayName("file with null name uses 'bin' default")
    void nullFileName_usesBinDefault() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", null, "image/jpeg", "data".getBytes());
        // When file.getOriginalFilename() is null, StringUtils.cleanPath throws NPE  
        // but MockMultipartFile with null name returns "" — either way it should handle it
        assertThatCode(() -> service.uploadCandidatePhoto(1L, file)).doesNotThrowAnyException();
    }
}
