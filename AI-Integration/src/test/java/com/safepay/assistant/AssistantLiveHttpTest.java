package com.safepay.assistant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.safepay.assistant.Models.*;
import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.fasterxml.jackson.databind.ObjectMapper;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class AssistantLiveHttpTest {
    @org.springframework.boot.test.context.TestConfiguration
    static class BlockingTestClient {
        @org.springframework.context.annotation.Bean
        org.springframework.boot.web.client.RestTemplateBuilder restTemplateBuilder() {
            return new org.springframework.boot.web.client.RestTemplateBuilder()
                .requestFactory(org.springframework.http.client.SimpleClientHttpRequestFactory.class);
        }
    }
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @MockitoBean SafePayClient backend;
    @MockitoBean ReviewService reviews;
    URI url(String path){return URI.create("http://127.0.0.1:"+port+"/assistant"+path);}
    String cookie(LocalHttp.Response r){return r.headers().allValues("Set-Cookie").stream().filter(s->s.startsWith("SAFEPAY_AI_SESSION=")).findFirst().orElseThrow().split(";",2)[0];}
    @Test void actualServerServesModulesAndMaintainsSeparateCsrfProtectedSession()throws Exception{
        var page=LocalHttp.send(url("/"),"GET",Map.of("Accept","*/*"),null,10);
        assertEquals(200,page.statusCode());assertTrue(page.body().contains("Admin Assistant"));assertTrue(page.headers().firstValue("Content-Security-Policy").isPresent());
        var module=LocalHttp.send(url("/format.mjs"),"GET",Map.of("Accept","*/*"),null,10);
        assertEquals(200,module.statusCode());assertTrue(module.headers().firstValue("Content-Type").orElse("").contains("javascript"));
        var session=LocalHttp.send(url("/api/session"),"GET",Map.of(),null,10);
        String before=cookie(session),token=json.readTree(session.body()).path("csrf").asText();
        assertFalse(token.isBlank());assertTrue(session.headers().allValues("Set-Cookie").toString().contains("HttpOnly"));
        assertEquals(401,LocalHttp.send(url("/api/pending"),"GET",Map.of("Cookie",before),null,10).statusCode());
        session=LocalHttp.send(url("/api/session"),"GET",Map.of(),null,10);before=cookie(session);token=json.readTree(session.body()).path("csrf").asText();
        var admin=new BackendSession("JSESSIONID=upstream",new Identity(7L,"Test Admin","admin@test","ADMIN","ACTIVE"));
        when(backend.login(any())).thenReturn(admin);when(backend.pending(admin)).thenReturn(List.of());
        var login=LocalHttp.send(url("/api/login"),"POST",Map.of("Cookie",before,"X-CSRF-TOKEN",token,"Content-Type","application/json"),"{\"email\":\"admin@test\",\"password\":\"TestOnly#2026\"}",10);
        assertEquals(200,login.statusCode());String after=cookie(login);assertNotEquals(before,after);
        var pending=LocalHttp.send(url("/api/pending"),"GET",Map.of("Cookie",after),null,10);assertEquals(200,pending.statusCode());assertEquals("[]",pending.body());
        assertFalse(login.body().contains("upstream"));assertFalse(login.body().contains("TestOnly"));
        assertEquals(403,LocalHttp.send(url("/api/payments/100/review"),"POST",Map.of("Cookie",after),"",10).statusCode());
        verifyNoInteractions(reviews);
    }
}
