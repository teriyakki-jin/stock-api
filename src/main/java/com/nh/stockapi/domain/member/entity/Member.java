package com.nh.stockapi.domain.member.entity;

import com.nh.stockapi.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(exclude = "password")
public class Member extends BaseEntity implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = true)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = true, length = 20, unique = true)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** Supabase Auth 사용자 UUID (OAuth 로그인 시 설정) */
    @Column(name = "supabase_uid", unique = true, length = 36)
    private String supabaseUid;

    /** 가입 경로 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AuthProvider provider;

    @Builder
    private Member(String email, String password, String name, String phone,
                   Role role, String supabaseUid, AuthProvider provider) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.role = role;
        this.supabaseUid = supabaseUid;
        this.provider = provider;
    }

    public static Member create(String email, String encodedPassword,
                                String name, String phone) {
        return Member.builder()
                .email(email)
                .password(encodedPassword)
                .name(name)
                .phone(phone)
                .role(Role.USER)
                .provider(AuthProvider.LOCAL)
                .build();
    }

    public static Member createOAuth(String email, String name,
                                     String supabaseUid, AuthProvider provider) {
        return Member.builder()
                .email(email)
                .name(name)
                .role(Role.USER)
                .supabaseUid(supabaseUid)
                .provider(provider)
                .build();
    }

    public void linkSupabase(String supabaseUid, AuthProvider provider) {
        this.supabaseUid = supabaseUid;
        this.provider = provider;
    }

    // UserDetails
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired()  { return true; }

    @Override
    public boolean isAccountNonLocked()   { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled()            { return true; }
}
