package com.employee.controller;
import com.employee.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@CrossOrigin(origins = "http://127.0.0.1:5500")
@RequestMapping("/api/auth")
public class AuthController {
    private final JwtUtil jwtUtil;

    public AuthController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestParam String username, @RequestParam String contact) {
        String uniqueSessionId = UUID.randomUUID().toString();
        Map<String, Object> claims = new HashMap<>();
        claims.put("uniquesessionid", uniqueSessionId);
        claims.put("contact", contact);
        String token = jwtUtil.generateToken(claims, username);
        return ResponseEntity.ok(Map.of(
                "token", token,
                "contact", contact,
                "useruniqueid", uniqueSessionId  // Added missing value
        ));
    }
}
