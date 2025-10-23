package ma.youcode.surevote.controller;

import ma.youcode.surevote.domain.enums.RoleUtilisateur;
import ma.youcode.surevote.dto.request.CreateUserRequest;
import ma.youcode.surevote.dto.request.UpdateRoleRequest;
import ma.youcode.surevote.dto.response.UserResponse;
import ma.youcode.surevote.service.UserService;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    @Mock private UserService userService;
    @InjectMocks private AdminUserController controller;

    @Test
    void createUser_shouldReturn201() {
        CreateUserRequest req = new CreateUserRequest();
        when(userService.createUser(req)).thenReturn(new UserResponse());
        ResponseEntity<UserResponse> response = controller.createUser(req);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void getAllUsers_noFilters_shouldReturn200() {
        when(userService.findAll()).thenReturn(List.of(new UserResponse()));
        ResponseEntity<List<UserResponse>> response = controller.getAllUsers(null, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void getAllUsers_withRoleFilter_shouldReturn200() {
        when(userService.findAllByRole(RoleUtilisateur.ADMIN)).thenReturn(List.of());
        ResponseEntity<List<UserResponse>> response = controller.getAllUsers(RoleUtilisateur.ADMIN, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getAllUsers_withKeyword_shouldReturn200() {
        when(userService.search("test")).thenReturn(List.of());
        ResponseEntity<List<UserResponse>> response = controller.getAllUsers(null, "test");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getAllUsersPaged_shouldReturn200() {
        Page<UserResponse> page = new PageImpl<>(List.of());
        when(userService.findAll(any())).thenReturn(page);
        ResponseEntity<Page<UserResponse>> response = controller.getAllUsersPaged(0, 20, "id", "desc");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getAllUsersPaged_ascSort_shouldReturn200() {
        Page<UserResponse> page = new PageImpl<>(List.of());
        when(userService.findAll(any())).thenReturn(page);
        ResponseEntity<Page<UserResponse>> response = controller.getAllUsersPaged(0, 20, "nom", "asc");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getUserById_shouldReturn200() {
        when(userService.findById(1L)).thenReturn(new UserResponse());
        ResponseEntity<UserResponse> response = controller.getUserById(1L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void updateUserRole_shouldReturn200() {
        UpdateRoleRequest req = mock(UpdateRoleRequest.class);
        when(req.role()).thenReturn(RoleUtilisateur.ADMIN);
        when(userService.updateRole(1L, RoleUtilisateur.ADMIN)).thenReturn(new UserResponse());
        ResponseEntity<UserResponse> response = controller.updateUserRole(1L, req);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void deactivateUser_shouldReturn204() {
        ResponseEntity<Void> response = controller.deactivateUser(1L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(userService).deactivateUser(1L);
    }

    @Test
    void activateUser_shouldReturn200() {
        when(userService.setAccountEnabled(1L, true)).thenReturn(new UserResponse());
        ResponseEntity<UserResponse> response = controller.activateUser(1L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void deactivateUserExplicit_shouldReturn200() {
        when(userService.setAccountEnabled(1L, false)).thenReturn(new UserResponse());
        ResponseEntity<UserResponse> response = controller.deactivateUserExplicit(1L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getUsersByRole_shouldReturn200() {
        when(userService.findAllByRole(RoleUtilisateur.ADMIN)).thenReturn(List.of());
        ResponseEntity<List<UserResponse>> response = controller.getUsersByRole(RoleUtilisateur.ADMIN);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getUserStats_shouldReturn200() {
        when(userService.countAll()).thenReturn(100L);
        when(userService.countByRole(any())).thenReturn(25L);
        when(userService.countActive()).thenReturn(90L);

        ResponseEntity<Map<String, Long>> response = controller.getUserStats();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("totalUsers");
    }

    @Test
    void getCurrentAdminProfile_shouldReturn200() {
        when(userService.getCurrentUserProfile()).thenReturn(new UserResponse());
        ResponseEntity<UserResponse> response = controller.getCurrentAdminProfile();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
