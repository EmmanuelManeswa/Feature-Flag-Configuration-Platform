package com.featureflagplatform.auth.service;

import com.featureflagplatform.auth.domain.User;
import com.featureflagplatform.auth.dto.CreateUserRequest;
import com.featureflagplatform.auth.dto.CreatedUserDto;
import com.featureflagplatform.auth.dto.UserDto;
import com.featureflagplatform.auth.repository.UserRepository;
import com.featureflagplatform.auth.security.PasswordGenerator;
import com.featureflagplatform.common.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Admin-facing user management: create an account with a backend-generated
 * password, list accounts, and disable/enable them. Deliberately no delete
 * — see {@code V7__add_user_enabled_flag.sql} for why (a user with any
 * flag/audit history can't be hard-deleted without violating a foreign key,
 * and audit rows must always resolve to a real actor).
 *
 * <p>Demo/assessment scope, stated plainly: this is how an admin
 * provisions accounts for other people to use in a demo or review
 * session, not a production user-management system — there is no
 * self-service signup, no password-reset-by-email flow, and no invite
 * link. A newly created account's password is returned exactly once, in
 * the HTTP response to the request that created it, and never logged.
 */
@Service
@Transactional(readOnly = true)
public class UserManagementService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserManagementService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Page<UserDto> list(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserDto::from);
    }

    @Transactional
    public CreatedUserDto create(CreateUserRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new IllegalArgumentException("A user with email '%s' already exists".formatted(request.email()));
        }

        String generatedPassword = PasswordGenerator.generate();
        User user = new User(request.email(), passwordEncoder.encode(generatedPassword), request.displayName(), request.role());
        user = userRepository.save(user);

        return new CreatedUserDto(UserDto.from(user), generatedPassword);
    }

    @Transactional
    public UserDto disable(UUID targetUserId, User actingAdmin) {
        if (targetUserId.equals(actingAdmin.getId())) {
            throw new IllegalArgumentException("You cannot disable your own account");
        }
        User target = findEntity(targetUserId);
        target.disable();
        return UserDto.from(userRepository.save(target));
    }

    @Transactional
    public UserDto enable(UUID targetUserId) {
        User target = findEntity(targetUserId);
        target.enable();
        return UserDto.from(userRepository.save(target));
    }

    private User findEntity(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
