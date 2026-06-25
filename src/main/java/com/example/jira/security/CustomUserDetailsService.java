package com.example.jira.security;


import com.example.jira.entity.User;
import com.example.jira.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;



@Service
public class CustomUserDetailsService implements UserDetailsService {
    @Autowired
    private UserRepository userRepository;


    @Override
    @Transactional
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        //tim user trong database
        User user = userRepository.findByEmail(email).orElseThrow(()-> new UsernameNotFoundException("User not found: " + email));

        //tra ve UserDetails cho Spring Security
        return UserPrincipal.fromUser(user);

    }
}
