package org.pms.silverocean.service.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.RoleService;
import org.pms.silverocean.service.auth.wrappers.RoleWrapper;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.security.KeyDao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class JwtService {
    public static final String ROLES = "roles";
    public static final String PERMISSIONS = "permissions";

    @Value("${spring.application.name}")
    private String SPRING_APPLICATION_NAME;

    private Key key;

    private final RoleService roleService;
    private final ConfigService configService;

    private final KeyDao keyDao;
    private final UserDao userDao;

    public JwtService(RoleService roleService, ConfigService configService, KeyDao keyDao, UserDao userDao) {
        this.roleService = roleService;
        this.configService = configService;
        this.keyDao = keyDao;
        this.userDao = userDao;
    }

    @PostConstruct
    private void init() {
        key = Keys.hmacShaKeyFor(keyDao.getActiveSecretKey());
    }

    public String generateJWT(String email) {
        Set<RoleWrapper> roles = roleService.getPermissionsForUser(email);
        return generateToken(email, roles);
    }

    private String generateToken(String subject, Set<RoleWrapper> roles) {
        List<Map<String, Object>> rolesClaim = roles.stream()
                .map(role -> {
                    Map<String, Object> roleJson = new HashMap<>();
                    roleJson.put("title", role.getRoleName());
                    roleJson.put("properties", role.getProperty());
                    roleJson.put(PERMISSIONS, role.getRolePermissions());
                    return roleJson;
                })
                .toList();

        return Jwts.builder()
                .setSubject(subject)
                .setIssuer(SPRING_APPLICATION_NAME)
                .setIssuedAt(new Date())
                .claim(ROLES, rolesClaim)
                .setExpiration(new Date(System.currentTimeMillis() + (getJwtValidityInSeconds() * 1000L)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // Validate and parse token
    public Jws<Claims> validateToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token);
    }

    public boolean checkIfRefreshTokenIsPresent(String email) {
        return userDao.findByEmail(email).map(Users::getRefreshToken).isPresent();
    }

    private int getJwtValidityInSeconds() {
        return configService.getConfigByName(PMSConfigs.JWT_VALIDITY_SECONDS).get().intValue();
    }

}
