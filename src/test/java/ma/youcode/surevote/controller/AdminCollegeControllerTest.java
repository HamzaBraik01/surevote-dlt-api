package ma.youcode.surevote.controller;

import ma.youcode.surevote.dto.request.AddVoterToCollegeRequest;
import ma.youcode.surevote.dto.request.CollegeRequest;
import ma.youcode.surevote.dto.response.CollegeResponse;
import ma.youcode.surevote.dto.response.UserResponse;
import ma.youcode.surevote.dto.response.endpoint.admin.CollegeMemberCountResponse;
import ma.youcode.surevote.dto.response.endpoint.admin.CollegeMembershipResponse;
import ma.youcode.surevote.mapper.endpoint.AdminEndpointMapper;
import ma.youcode.surevote.service.CollegeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminCollegeControllerTest {

    @Mock private CollegeService collegeService;
    @Mock private AdminEndpointMapper adminEndpointMapper;
    @InjectMocks private AdminCollegeController controller;

    @Test
    void createCollege_shouldReturn201() {
        CollegeRequest req = new CollegeRequest();
        when(collegeService.create(req)).thenReturn(new CollegeResponse());
        ResponseEntity<CollegeResponse> response = controller.createCollege(req);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void getAllColleges_noKeyword_shouldReturn200() {
        when(collegeService.findAll()).thenReturn(List.of(new CollegeResponse()));
        ResponseEntity<List<CollegeResponse>> response = controller.getAllColleges(null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void getAllColleges_withKeyword_shouldReturn200() {
        when(collegeService.search("test")).thenReturn(List.of());
        ResponseEntity<List<CollegeResponse>> response = controller.getAllColleges("test");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getAllCollegesPaged_descSort_shouldReturn200() {
        Page<CollegeResponse> page = new PageImpl<>(List.of());
        when(collegeService.findAll(any())).thenReturn(page);
        ResponseEntity<Page<CollegeResponse>> response = controller.getAllCollegesPaged(0, 20, "nom", "desc");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getAllCollegesPaged_ascSort_shouldReturn200() {
        Page<CollegeResponse> page = new PageImpl<>(List.of());
        when(collegeService.findAll(any())).thenReturn(page);
        ResponseEntity<Page<CollegeResponse>> response = controller.getAllCollegesPaged(0, 20, "nom", "asc");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getCollegeById_shouldReturn200() {
        when(collegeService.findById(1L)).thenReturn(new CollegeResponse());
        ResponseEntity<CollegeResponse> response = controller.getCollegeById(1L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void updateCollege_shouldReturn200() {
        CollegeRequest req = new CollegeRequest();
        when(collegeService.update(1L, req)).thenReturn(new CollegeResponse());
        ResponseEntity<CollegeResponse> response = controller.updateCollege(1L, req);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void deleteCollege_shouldReturn204() {
        ResponseEntity<Void> response = controller.deleteCollege(1L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(collegeService).delete(1L);
    }

    @Test
    void getCollegeMembers_shouldReturn200() {
        when(collegeService.findMembersByCollegeId(1L)).thenReturn(List.of(new UserResponse()));
        ResponseEntity<List<UserResponse>> response = controller.getCollegeMembers(1L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void addVoterToCollege_shouldReturn200() {
        AddVoterToCollegeRequest req = mock(AddVoterToCollegeRequest.class);
        when(req.electeurId()).thenReturn(10L);
        when(collegeService.addVoterToCollege(1L, 10L)).thenReturn(new CollegeResponse());
        ResponseEntity<CollegeResponse> response = controller.addVoterToCollege(1L, req);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void removeVoterFromCollege_shouldReturn200() {
        when(collegeService.removeVoterFromCollege(1L, 10L)).thenReturn(new CollegeResponse());
        ResponseEntity<CollegeResponse> response = controller.removeVoterFromCollege(1L, 10L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void countMembers_shouldReturn200() {
        when(collegeService.countMembers(1L)).thenReturn(5L);
        CollegeMemberCountResponse countResp = new CollegeMemberCountResponse();
        when(adminEndpointMapper.toCollegeMemberCountResponse(1L, 5L)).thenReturn(countResp);

        ResponseEntity<CollegeMemberCountResponse> response = controller.countMembers(1L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void checkMembership_shouldReturn200() {
        when(collegeService.isVoterInCollege(1L, 10L)).thenReturn(true);
        CollegeMembershipResponse membershipResp = new CollegeMembershipResponse();
        when(adminEndpointMapper.toCollegeMembershipResponse(1L, 10L, true)).thenReturn(membershipResp);

        ResponseEntity<CollegeMembershipResponse> response = controller.checkMembership(1L, 10L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
