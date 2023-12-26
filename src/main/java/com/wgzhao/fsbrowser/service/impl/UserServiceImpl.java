// package com.wgzhao.fsbrowser.service.impl;

// import com.wgzhao.fsbrowser.model.oracle.Role;
// import com.wgzhao.fsbrowser.model.oracle.User;
// import com.wgzhao.fsbrowser.repository.oracle.UserRepo;
// import com.wgzhao.fsbrowser.service.UserService;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.security.core.GrantedAuthority;
// import org.springframework.security.core.authority.SimpleGrantedAuthority;
// import org.springframework.security.core.userdetails.UserDetails;
// import org.springframework.security.core.userdetails.UsernameNotFoundException;
// import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
// import org.springframework.stereotype.Service;

// import java.util.Collection;
// import java.util.List;
// import java.util.stream.Collectors;

// @Service
// public class UserServiceImpl implements UserService {

//     @Autowired
//     private UserRepo userRepo;

//     @Autowired
//     private BCryptPasswordEncoder bCryptPasswordEncoder;


//     @Override
//     public User save(User user) {
//         user.setPassword(bCryptPasswordEncoder.encode(user.getPassword()));
//         user.setRoles(List.of(new Role("ROLE_USER")));
//         return userRepo.save(user);
//     }

//     @Override
//     public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
//         User user = userRepo.findByUsername(username);
//         if (user == null) {
//             throw new UsernameNotFoundException("Invalid username or password.");
//         }
//         return new org.springframework.security.core.userdetails.User(user.getUsername(),
//                 user.getPassword(),
//                 mapRolesToAuthorities(user.getRoles()));
//     }

//     private Collection<? extends GrantedAuthority> mapRolesToAuthorities(Collection<Role> roles){
//         return roles.stream().map(role -> new SimpleGrantedAuthority(role.getName())).collect(Collectors.toList());
//     }

//     @Override
//     public boolean existsByUsername(String username) {
//         return userRepo.existsByUsername(username);
//     }
// }
