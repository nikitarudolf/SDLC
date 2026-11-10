package by.bsuir.labqueue.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByToken(String token);

    List<User> findByRoleOrderByFullName(Role role);

    List<User> findByGroupIdOrderByFullName(Long groupId);
}
