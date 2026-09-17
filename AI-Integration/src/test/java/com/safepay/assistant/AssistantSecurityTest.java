package com.safepay.assistant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.safepay.assistant.Models.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest @AutoConfigureMockMvc
class AssistantSecurityTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;
    @MockitoBean SafePayClient backend;@MockitoBean ReviewService reviews;
    BackendSession admin=new BackendSession("JSESSIONID=secret-upstream",new Identity(7L,"Admin","admin@test","ADMIN","ACTIVE"));
    @BeforeEach void allowAdmin(){when(backend.pending(admin)).thenReturn(List.of());}
    MockHttpSession session(){var s=new MockHttpSession();s.setAttribute(AssistantController.SESSION,admin);return s;}
    String csrf(MockHttpSession s)throws Exception{return json.readTree(mvc.perform(get("/api/session").session(s)).andReturn().getResponse().getContentAsString()).path("csrf").asText();}
    @Test void anonymousCannotReadEvidenceOrPending()throws Exception{
        mvc.perform(get("/api/pending")).andExpect(status().isUnauthorized());mvc.perform(get("/api/payments/100/evidence")).andExpect(status().isUnauthorized());verifyNoInteractions(reviews,backend);
    }
    @Test void csrfIsRequiredForLoginAndReview()throws Exception{
        mvc.perform(post("/api/login").contentType("application/json").content("{\"email\":\"admin@test\",\"password\":\"pass\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/payments/100/review").session(session())).andExpect(status().isForbidden());verifyNoInteractions(reviews,backend);
    }
    @Test void reviewUsesServerSessionAndPathId()throws Exception{
        var s=session();String token=csrf(s);
        mvc.perform(post("/api/payments/100/review").session(s).header("X-CSRF-TOKEN",token).param("userId","999")).andExpect(status().isOk());
        verify(reviews).review(admin,100,true);
    }
    @Test void backendRoleRevocationClearsLocalSession()throws Exception{
        var s=session();when(backend.pending(admin)).thenThrow(new AppError(403,"Revoked"));
        mvc.perform(get("/api/pending").session(s)).andExpect(status().isForbidden()).andExpect(header().string("Cache-Control","no-store"));assertTrue(s.isInvalid());
    }
    @Test void sessionNeverExposesUpstreamCookie()throws Exception{
        mvc.perform(get("/api/session").session(session())).andExpect(status().isOk()).andExpect(jsonPath("$.identity.role").value("ADMIN"))
            .andExpect(jsonPath("$.cookie").doesNotExist()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-upstream"))));
    }
    @Test void logoutInvalidatesAssistantAndItsUpstreamSession()throws Exception{
        var s=session();String token=csrf(s);mvc.perform(post("/api/logout").session(s).header("X-CSRF-TOKEN",token)).andExpect(status().isOk());
        assertTrue(s.isInvalid());verify(backend).logout(admin);
    }
    @Test void signInReplacesSessionAndDoesNotReturnCredentials()throws Exception{
        var s=new MockHttpSession();String token=csrf(s);when(backend.login(any())).thenReturn(admin);
        mvc.perform(post("/api/login").session(s).header("X-CSRF-TOKEN",token).contentType("application/json").content("{\"email\":\"admin@test\",\"password\":\"example\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.role").value("ADMIN"));assertTrue(s.isInvalid());
    }
    @Test void unknownLoginFieldsAreRejected()throws Exception{
        var s=new MockHttpSession();String token=csrf(s);
        mvc.perform(post("/api/login").session(s).header("X-CSRF-TOKEN",token).contentType("application/json").content("{\"email\":\"a\",\"password\":\"p\",\"role\":\"ADMIN\"}"))
            .andExpect(status().isBadRequest());verify(backend,never()).login(any());
    }
    @Test void noApprovalRouteExistsAndStaticUiIsProtectedByCsp()throws Exception{
        var s=session();String token=csrf(s);
        mvc.perform(post("/api/payments/100/approve").session(s).header("X-CSRF-TOKEN",token)).andExpect(status().is4xxClientError());
        mvc.perform(get("/index.html")).andExpect(status().isOk()).andExpect(header().exists("Content-Security-Policy"));verifyNoInteractions(reviews);
    }
}
