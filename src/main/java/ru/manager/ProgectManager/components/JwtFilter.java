package ru.manager.ProgectManager.components;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;
import ru.manager.ProgectManager.enums.TokenStatus;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.NoSuchElementException;

@Component
public class JwtFilter extends GenericFilterBean {
    private JwtProvider jwtProvider;

    @Autowired
    private void setJwtProvider(JwtProvider j) {
        jwtProvider = j;
    }

    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private void setCustomUserDetailsService(CustomUserDetailsService c) {
        customUserDetailsService = c;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        String token = getTokenFromRequest((HttpServletRequest) servletRequest);
        if (token != null && jwtProvider.validateToken(token) == TokenStatus.OK) {
            try {
                String userLogin = jwtProvider.getLoginFromToken(token);
                UserDetails customUserDetails = customUserDetailsService.loadUserByUsername(userLogin);
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(customUserDetails,
                        null, customUserDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (NoSuchElementException ignore) {
            }
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            return Arrays.stream(cookies)
                    .filter(c -> c.getName().equals("access"))
                    .findAny()
                    .map(Cookie::getValue)
                    .orElse(null);
        } else {
            return null;
        }
    }
}
