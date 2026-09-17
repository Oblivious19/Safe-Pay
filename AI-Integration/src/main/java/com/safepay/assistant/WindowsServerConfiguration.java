package com.safepay.assistant;
import org.springframework.context.annotation.*;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
@Configuration
public class WindowsServerConfiguration {
    @Bean TomcatServletWebServerFactory assistantWebServer(){
        var factory=new TomcatServletWebServerFactory();
        // IOCP on Windows avoids the JDK 17 selector wake-up socket issue; portable on other JDK platforms.
        factory.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");return factory;
    }
}
