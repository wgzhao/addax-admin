package com.wgzhao.fsbrowser.repository;

import com.wgzhao.fsbrowser.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepo extends JpaRepository<User, Long> {

    User findByEmail(String email);

    User findByUsername(String username);
}
