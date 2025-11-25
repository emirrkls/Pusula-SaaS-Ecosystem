package com.pusula.backend.controller;

import com.pusula.backend.dto.UserDTO;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.UserRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @GetMapping
    public ResponseEntity<List<UserDTO>> getUsers(@RequestParam(required = false) String role) {
        User currentUser = getCurrentUser();
        List<User> users = userRepository.findByCompanyId(currentUser.getCompanyId());

        if (role != null && !role.isEmpty()) {
            users = users.stream()
                    .filter(u -> u.getRole().equals(role))
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok(mapToDTOs(users));
    }

    @GetMapping("/technicians")
    public ResponseEntity<List<UserDTO>> getTechnicians() {
        User currentUser = getCurrentUser();
        List<User> users = userRepository.findByCompanyId(currentUser.getCompanyId());

        // Filter for technicians - checking for both "TECHNICIAN" and "ADMIN" for now
        // since sample data might be mixed
        List<User> technicians = users.stream()
                .filter(u -> "TECHNICIAN".equalsIgnoreCase(u.getRole()) || "ADMIN".equalsIgnoreCase(u.getRole()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(mapToDTOs(technicians));
    }

    private List<UserDTO> mapToDTOs(List<User> users) {
        return users.stream()
                .map(u -> UserDTO.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .fullName(u.getFullName())
                        .role(u.getRole())
                        .build())
                .collect(Collectors.toList());
    }
}
