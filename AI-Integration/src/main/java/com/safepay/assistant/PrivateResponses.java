package com.safepay.assistant;
import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component
public class PrivateResponses extends OncePerRequestFilter {
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        if(request.getRequestURI().contains("/api/")){response.setHeader("Cache-Control","no-store");response.setHeader("Pragma","no-cache");}
        chain.doFilter(request,response);
    }
}
