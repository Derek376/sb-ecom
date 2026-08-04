package com.ecommerce.project.service;

import com.ecommerce.project.model.AppRole;
import com.ecommerce.project.model.Role;
import com.ecommerce.project.model.User;
import com.ecommerce.project.repositories.RoleRepository;
import com.ecommerce.project.repositories.UserRepository;
import com.ecommerce.project.security.jwt.JwtUtils;
import com.ecommerce.project.security.request.SignupRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder encoder;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void registerAlwaysAssignsOnlyUserRole() {
        SignupRequest request = new SignupRequest();
        request.setUsername("newuser");
        request.setEmail("newuser@example.com");
        request.setPassword("password123");

        Role userRole = new Role(AppRole.ROLE_USER);
        when(encoder.encode("password123")).thenReturn("encoded-password");
        when(roleRepository.findByRoleName(AppRole.ROLE_USER))
                .thenReturn(Optional.of(userRole));

        authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getRoles()).containsExactly(userRole);
        verify(roleRepository, never()).findByRoleName(AppRole.ROLE_ADMIN);
        verify(roleRepository, never()).findByRoleName(AppRole.ROLE_SELLER);
    }

    @Test
    void adminSellerRegistrationAssignsUserAndSellerRolesWithoutAcceptingClientRoles() {
        SignupRequest request = new SignupRequest();
        request.setUsername("seller");
        request.setEmail("seller@example.com");
        request.setPassword("password123");

        Role userRole = new Role(AppRole.ROLE_USER);
        Role sellerRole = new Role(AppRole.ROLE_SELLER);
        when(encoder.encode("password123")).thenReturn("encoded-password");
        when(roleRepository.findByRoleName(AppRole.ROLE_USER)).thenReturn(Optional.of(userRole));
        when(roleRepository.findByRoleName(AppRole.ROLE_SELLER)).thenReturn(Optional.of(sellerRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setUserId(42L);
            return saved;
        });

        authService.registerSeller(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getRoles()).containsExactlyInAnyOrder(userRole, sellerRole);
        verify(roleRepository, never()).findByRoleName(AppRole.ROLE_ADMIN);
    }

    @Test
    void duplicateUsernameIsRejectedBeforePasswordEncoding() {
        SignupRequest request = signupRequest("existing", "new@example.com");
        when(userRepository.existsByUserName("existing")).thenReturn(true);

        var response = authService.register(request);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(response.getBody().getMessage()).contains("Username is already taken");
        verifyNoInteractions(encoder, roleRepository);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void duplicateEmailIsRejectedBeforePasswordEncoding() {
        SignupRequest request = signupRequest("newuser", "existing@example.com");
        when(userRepository.existsByUserName("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        var response = authService.register(request);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(response.getBody().getMessage()).contains("Email is already taken");
        verifyNoInteractions(encoder, roleRepository);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void duplicateSellerUsernameRaisesADomainErrorWithoutSaving() {
        SignupRequest request = signupRequest("existing", "seller@example.com");
        when(userRepository.existsByUserName("existing")).thenReturn(true);

        assertThatThrownBy(() -> authService.registerSeller(request))
                .isInstanceOf(com.ecommerce.project.exceptions.APIexception.class)
                .hasMessage("Username is already taken");

        verifyNoInteractions(encoder, roleRepository);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void sellerCreationFailsClosedWhenSellerRoleIsNotConfigured() {
        SignupRequest request = signupRequest("seller", "seller@example.com");
        Role userRole = new Role(AppRole.ROLE_USER);
        when(roleRepository.findByRoleName(AppRole.ROLE_USER)).thenReturn(Optional.of(userRole));
        when(roleRepository.findByRoleName(AppRole.ROLE_SELLER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.registerSeller(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SELLER role is not configured");

        verify(encoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    private SignupRequest signupRequest(String username, String email) {
        SignupRequest request = new SignupRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setPassword("password123");
        return request;
    }
}
