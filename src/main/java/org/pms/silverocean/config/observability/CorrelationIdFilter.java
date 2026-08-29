package org.pms.silverocean.config.observability;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component @Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER="X-Correlation-ID";private static final String MDC_KEY="correlationId";
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        String supplied=request.getHeader(HEADER);String id=isValid(supplied)?supplied:UUID.randomUUID().toString();
        MDC.put(MDC_KEY,id);response.setHeader(HEADER,id);try{chain.doFilter(request,response);}finally{MDC.remove(MDC_KEY);}
    }
    private boolean isValid(String value){return value!=null&&value.length()<=64&&value.matches("[A-Za-z0-9._:-]+");}
}
