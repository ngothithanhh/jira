package com.example.jira.security;

import com.example.jira.entity.User;
import com.example.jira.entity.UserRoleAssignment;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class UserPrincipal implements UserDetails {
    private int id;
    private String email;
    private String password;
    private Collection<? extends GrantedAuthority> authorities;


    public UserPrincipal() {
    }

    public UserPrincipal(int id, String email,String password, Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.password = password;
        this.email = email;
        this.authorities = authorities;

    }

    public static UserPrincipal fromUser(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if(user.getUserRoleAssignments() != null){
            for (UserRoleAssignment assignment : user.getUserRoleAssignments()){
                authorities.add(new SimpleGrantedAuthority("ROLE_" + assignment.getRole().getRoleName().toUpperCase()));
            }
        }
        return new UserPrincipal(
                user.getUserId(),
                user.getEmail(),
                user.getPasswordHash(),
                authorities
        );
    }

    public int getId() {
        return id;
    }


    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities(){
        return authorities;
    }

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
