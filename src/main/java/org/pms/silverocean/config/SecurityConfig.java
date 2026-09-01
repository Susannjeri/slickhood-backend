package org.pms.silverocean.config;

import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.auth.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@EnableWebSecurity @Configuration @EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    private final JwtService jwtService;
    private final I18NService i18NService;

    private final SimpleCorsFilter corsFilter;
    private final AccountActivationFilter accountActivationFilter;

    public SecurityConfig(JwtService jwtService, I18NService i18NService, SimpleCorsFilter corsFilter,
                          AccountActivationFilter accountActivationFilter) {
        this.jwtService = jwtService;
        this.i18NService = i18NService;
        this.corsFilter = corsFilter;
        this.accountActivationFilter = accountActivationFilter;
    }


    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/logout").authenticated()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/callback/**").permitAll()
                        .requestMatchers("/smart-gate/device/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/invite/validate").permitAll()
                        .requestMatchers("/lease/view/template").permitAll()
                        .requestMatchers("/otp/qrcode").authenticated()
                        .requestMatchers("/otp/**").permitAll()
                        .requestMatchers("/property/unit/charges").permitAll()
                        .requestMatchers("/property/unit/type").permitAll()
                        .requestMatchers("/property/type").permitAll()
                        .requestMatchers("/property/image/**").permitAll()
                        .requestMatchers("/role/list").permitAll()
                        .requestMatchers("/deployed-hash").permitAll()
                        .requestMatchers("/sp/directory/**").permitAll()
                        .requestMatchers("/soko/catalog/**").permitAll()
                        .requestMatchers("/affiliate/public/**").permitAll()
                        .requestMatchers("/helpdesk/public/**").permitAll()
                        .anyRequest().authenticated()
                )
                .cors(cors -> cors.configurationSource(corsFilter.corsConfigurationSource()))
                .addFilterBefore(new JWTFilter(this.jwtService, this.i18NService), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(accountActivationFilter, JWTFilter.class);


        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public FilterRegistrationBean<AccountActivationFilter> disableContainerRegistration(
            AccountActivationFilter filter) {
        FilterRegistrationBean<AccountActivationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
