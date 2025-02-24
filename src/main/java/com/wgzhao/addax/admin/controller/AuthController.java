package com.wgzhao.addax.admin.controller;


import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.dto.AuthRequestDTO;
import com.wgzhao.addax.admin.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;


    @PostMapping("/login")
    public ApiResponse<String> AuthenticateAndGetToken(@RequestBody AuthRequestDTO authRequestDTO){
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(authRequestDTO.getUsername(), authRequestDTO.getPassword()));
        if(authentication.isAuthenticated()){
            return ApiResponse.success(jwtService.generateToken(authRequestDTO.getUsername()));

        } else {
            return ApiResponse.error(401, "failed to authenticate user");
        }

    }
}
