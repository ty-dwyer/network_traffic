package com.example.networktraffic.services;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.networktraffic.entities.User;
import com.example.networktraffic.repositories.UserRepository;
import com.example.networktraffic.util.JwtUtil;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public boolean register(String username, String password) {
        Optional<User> optionalUser = userRepository.findByUsername(username);

        if (optionalUser.isPresent()) {
            return false;
        } else {
            User newUser = new User();
            newUser.setUsername(username);
            newUser.setPassword(passwordEncoder.encode(password));
            userRepository.save(newUser);
            return true;
        }
    }

    public Optional<String> login(String username, String password) {
        Optional<User> optionalUser = userRepository.findByUsername(username);
    
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            String storedHash = user.getPassword();
            boolean matches = passwordEncoder.matches(password, storedHash);
            if (matches) {
                return Optional.of(jwtUtil.generateToken(username));
            } else {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }
}