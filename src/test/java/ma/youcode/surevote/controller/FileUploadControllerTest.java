package ma.youcode.surevote.controller;

import ma.youcode.surevote.dto.response.FileUploadResponse;
import ma.youcode.surevote.service.FileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileUploadControllerTest {

    @Mock private FileStorageService fileStorageService;
    @InjectMocks private FileUploadController controller;

    @Test
    void uploadCandidatePhoto_shouldReturn201() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        FileUploadResponse resp = new FileUploadResponse();
        when(fileStorageService.uploadCandidatePhoto(1L, file)).thenReturn(resp);

        ResponseEntity<FileUploadResponse> response = controller.uploadCandidatePhoto(1L, file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(resp);
    }

    @Test
    void uploadCandidateProgram_shouldReturn201() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        FileUploadResponse resp = new FileUploadResponse();
        when(fileStorageService.uploadCandidateProgramPdf(1L, file)).thenReturn(resp);

        ResponseEntity<FileUploadResponse> response = controller.uploadCandidateProgram(1L, file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(resp);
    }

    @Test
    void uploadElectionFile_shouldReturn201() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        FileUploadResponse resp = new FileUploadResponse();
        when(fileStorageService.uploadElectionFile(1L, file)).thenReturn(resp);

        ResponseEntity<FileUploadResponse> response = controller.uploadElectionFile(1L, file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(resp);
    }

    @Test
    void deleteFile_shouldReturn204() throws IOException {
        ResponseEntity<Void> response = controller.deleteFile("candidates/1/photo.jpg");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(fileStorageService).deleteFile("candidates/1/photo.jpg");
    }

    @Test
    void downloadFile_shouldReturn200() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/files/download/photo.jpg");
        when(fileStorageService.getFileContent("photo.jpg")).thenReturn("data".getBytes());

        ResponseEntity<byte[]> response = controller.downloadFile(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void downloadFile_invalidPath_shouldReturn404() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/other/path");

        ResponseEntity<byte[]> response = controller.downloadFile(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void downloadFile_IOException_shouldReturn404() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/files/download/missing.jpg");
        when(fileStorageService.getFileContent("missing.jpg")).thenThrow(new IOException("Not found"));

        ResponseEntity<byte[]> response = controller.downloadFile(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
