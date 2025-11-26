package com.pusula.backend.controller;

import com.pusula.backend.dto.UserDTO;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.UserRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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

    /**
     * Create a new user with encrypted password
     */
    @PostMapping
    public ResponseEntity<UserDTO> createUser(@RequestBody UserDTO userDTO) {
        User currentUser = getCurrentUser();

        // Validate password is provided
        if (userDTO.getPassword() == null || userDTO.getPassword().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Create new user
        User newUser = User.builder()
                .username(userDTO.getUsername())
                .passwordHash(passwordEncoder.encode(userDTO.getPassword())) // BCrypt hash
                .fullName(userDTO.getFullName())
                .role(userDTO.getRole())
                .companyId(currentUser.getCompanyId()) // Same company as creator
                .build();

        User saved = userRepository.save(newUser);

        return ResponseEntity.ok(mapToDTO(saved));
    }

    /**
     * Update an existing user (password is optional)
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserDTO> updateUser(@PathVariable Long id, @RequestBody UserDTO userDTO) {
        User currentUser = getCurrentUser();

        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Security: Ensure user belongs to same company
        if (!existingUser.getCompanyId().equals(currentUser.getCompanyId())) {
            return ResponseEntity.status(403).build();
        }

        // Update fields
        existingUser.setUsername(userDTO.getUsername());
        existingUser.setFullName(userDTO.getFullName());
        existingUser.setRole(userDTO.getRole());

        // Update password only if provided
        if (userDTO.getPassword() != null && !userDTO.getPassword().isEmpty()) {
            existingUser.setPasswordHash(passwordEncoder.encode(userDTO.getPassword()));
        }

        User updated = userRepository.save(existingUser);

        return ResponseEntity.ok(mapToDTO(updated));
    }

    /**
     * Delete a user (soft delete)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        User currentUser = getCurrentUser();

        User userToDelete = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Security: Ensure user belongs to same company
        if (!userToDelete.getCompanyId().equals(currentUser.getCompanyId())) {
            return ResponseEntity.status(403).build();
        }

        // Prevent self-deletion
        if (userToDelete.getId().equals(currentUser.getId())) {
            return ResponseEntity.status(400).build(); // Bad Request
        }

        userRepository.delete(userToDelete); // Soft delete via @SQLDelete

        return ResponseEntity.ok().build();
    }

    /**
     * Reset user password
     */
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        User currentUser = getCurrentUser();

        String newPassword = payload.get("password");
        if (newPassword == null || newPassword.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        User userToReset = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Security: Ensure user belongs to same company
        if (!userToReset.getCompanyId().equals(currentUser.getCompanyId())) {
            return ResponseEntity.status(403).build();
        }

        userToReset.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(userToReset);

        return ResponseEntity.ok().build();
    }

    private UserDTO mapToDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole())
                .build(); // Don't include password in response
    }
}
