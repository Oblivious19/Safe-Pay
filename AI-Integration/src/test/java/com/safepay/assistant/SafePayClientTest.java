package com.safepay.assistant;
import static org.junit.jupiter.api.Assertions.*;import static com.safepay.assistant.Models.*;
import java.util.*;import java.util.concurrent.atomic.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
class SafePayClientTest {
    MiniServer server;SafePayClient client;AtomicReference<String> role=new AtomicReference<>("ADMIN");AtomicInteger pendingStatus=new AtomicInteger(200);
    List<MiniServer.Request> requests=new java.util.concurrent.CopyOnWriteArrayList<>();
    @BeforeEach void setup()throws Exception{
        server=new MiniServer(request->{requests.add(request);
            if(request.path().equals("/api/auth/login"))return new MiniServer.Response(200,Map.of("Set-Cookie","JSESSIONID=private-token; HttpOnly; Path=/"),
                "{\"userId\":7,\"name\":\"Admin\",\"email\":\"admin@test\",\"role\":\""+role.get()+"\",\"status\":\"ACTIVE\"}");
            if(request.path().equals("/api/admin/transactions/hard-holds"))return new MiniServer.Response(pendingStatus.get(),Map.of("X-CSRF-TOKEN","token"),"[]");
            if(request.path().equals("/api/auth/logout"))return new MiniServer.Response(200,Map.of(),"{}");
            return new MiniServer.Response(404,Map.of(),"{}");
        });client=new SafePayClient(server.url(),new ObjectMapper().findAndRegisterModules());
    }
    @AfterEach void close()throws Exception{server.close();}
    @Test void adminAuthenticatesThroughExistingBackendAndCookieStaysServerSide(){
        var session=client.login(new LoginInput("admin@test",null,"Example#2026"));assertEquals("ADMIN",session.identity().role());
        assertEquals("JSESSIONID=private-token",requests.get(1).headers().get("cookie"));assertEquals("GET",requests.get(1).method());
        client.logout(session);assertTrue(requests.stream().anyMatch(r->r.path().equals("/api/auth/logout") && "token".equals(r.headers().get("x-csrf-token"))));
    }
    @Test void customerLoginIsRejectedAndUpstreamSessionIsLoggedOut(){role.set("CUSTOMER");assertEquals(403,assertThrows(AppError.class,()->client.login(new LoginInput("customer@test",null,"Example#2026"))).status());assertTrue(requests.stream().anyMatch(r->r.path().equals("/api/auth/logout")));}
    @Test void revokedAdminAndExpiredSessionsAreDenied(){var session=client.login(new LoginInput("admin@test",null,"Example#2026"));pendingStatus.set(403);assertEquals(403,assertThrows(AppError.class,()->client.pending(session)).status());pendingStatus.set(401);assertEquals(401,assertThrows(AppError.class,()->client.pending(session)).status());}
    @Test void arbitraryIdentityFieldsAndInvalidPasswordsCannotReachBackend(){assertThrows(AppError.class,()->client.login(new LoginInput("a","9876543210","p")));assertThrows(AppError.class,()->client.login(new LoginInput("a",null,"")));assertTrue(requests.isEmpty());}
    @Test void noMissingPaymentCanBeReviewed(){var session=client.login(new LoginInput("admin@test",null,"Example#2026"));assertEquals(409,assertThrows(AppError.class,()->client.pending(session,100)).status());}
}
