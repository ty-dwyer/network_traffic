package com.example.networktraffic.controllers;

import java.util.Optional;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.networktraffic.dto.LoginRequest;
import com.example.networktraffic.dto.RegisterRequest;
import com.example.networktraffic.services.AuthService;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/register")
    public String register(@RequestBody RegisterRequest request) {
        boolean success = authService.register(request.getUsername(), request.getPassword());

        if (success) {
            return "User registered successfully";
        } else {
            return "Username already taken";
        }
    }


    @PostMapping("/auth/login")
    public String login(@RequestBody LoginRequest request){
        Optional<String> optionalToken = authService.login(request.getUsername(), request.getPassword());

        if (optionalToken.isPresent()){

            return optionalToken.get();
        }
        else{
            return "Invalid credentials";
        }
    }
}