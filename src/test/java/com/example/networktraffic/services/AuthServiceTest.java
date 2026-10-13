package com.example.networktraffic.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.networktraffic.entities.User;
import com.example.networktraffic.repositories.UserRepository;
import com.example.networktraffic.util.JwtUtil;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;

    @Test
    void registrationStoresEncodedPassword() {
        when(userRepository.findByUsername("demo")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("bcrypt-hash");

        boolean registered = new AuthService(userRepository, passwordEncoder, jwtUtil).register("demo", "secret");

        assertThat(registered).isTrue();
        org.mockito.ArgumentCaptor<User> user = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(user.capture());
        assertThat(user.getValue().getPassword()).isEqualTo("bcrypt-hash");
    }

    @Test
    void loginReturnsTokenOnlyForMatchingPassword() {
        User user = new User();
        user.setUsername("demo");
        user.setPassword("bcrypt-hash");
        when(userRepository.findByUsername("demo")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "bcrypt-hash")).thenReturn(true);
        when(jwtUtil.generateToken("demo")).thenReturn("signed-token");

        assertThat(new AuthService(userRepository, passwordEncoder, jwtUtil).login("demo", "secret"))
                .contains("signed-token");
    }
}
