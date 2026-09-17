package com.safepay.assistant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.safepay.assistant.Models.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
class ReviewServiceTest {
    SafePayClient backend=mock(SafePayClient.class);EvidenceRepository repository=mock(EvidenceRepository.class);OllamaReviewer ai=mock(OllamaReviewer.class);
    BackendSession session=new BackendSession("JSESSIONID=private",new Identity(7L,"Admin","admin@test","ADMIN","ACTIVE"));
    Pending pending=new Pending(100L,"TX-100","120000.00",8L,"Customer",1L,"account","Recipient","bank","ifsc","rent","saved","date");
    Evidence evidence=new Evidence(100L,"TX-100",3L,8L,1L,"120000.00","HARD_HOLD","VERY_HIGH","saved","Recipient","masked","rent",LocalDateTime.now(),Instant.now(),"scope",List.of(new Fact("HISTORY","History","Verified fact",List.of())),List.of());
    ReviewService service=new ReviewService(backend,repository,ai,new ObjectMapper().findAndRegisterModules());
    @BeforeEach void setup(){when(backend.pending(session,100)).thenReturn(pending);when(repository.read(pending)).thenReturn(evidence);when(ai.model()).thenReturn("local-model");}
    @Test void evidenceOnlyDoesNotCallTheModel(){var result=service.review(session,100,false);assertEquals("EVIDENCE_ONLY",result.mode());assertNull(result.model());verifyNoInteractions(ai);assertEquals(64,result.evidenceSha256().length());}
    @Test void aiHighlightsOnlyVerifiedFacts(){when(ai.review(evidence)).thenReturn(new AiSelection(List.of("HISTORY"),List.of("CONFIRM_PURPOSE")));var result=service.review(session,100,true);assertEquals("AI_ASSISTED",result.mode());assertEquals("Verified fact",result.focus().get(0).text());verify(backend,times(2)).pending(session,100);verify(repository).requireUnchanged(100,3);}
    @Test void unavailableAiFallsBackHonestlyWithoutLosingEvidence(){when(ai.review(evidence)).thenThrow(new AppError(503,"Model unavailable"));var result=service.review(session,100,true);assertEquals("EVIDENCE_ONLY",result.mode());assertEquals("Model unavailable",result.message());assertTrue(result.focus().isEmpty());assertEquals(evidence,result.evidence());}
    @Test void revokedAdminCannotPublishLateModelResult(){when(ai.review(evidence)).thenAnswer(call->{when(backend.pending(session,100)).thenThrow(new AppError(403,"Revoked"));return new AiSelection(List.of("HISTORY"),List.of("CONFIRM_PURPOSE"));});assertEquals(403,assertThrows(AppError.class,()->service.review(session,100,true)).status());}
    @Test void settledPaymentCannotPublishStaleReview(){doThrow(new AppError(409,"Settled")).when(repository).requireUnchanged(100,3);assertEquals(409,assertThrows(AppError.class,()->service.review(session,100,false)).status());}
    @Test void unauthorizedRequestCannotReadDatabaseOrCallModel(){when(backend.pending(session,100)).thenThrow(new AppError(401,"Expired"));assertEquals(401,assertThrows(AppError.class,()->service.review(session,100,true)).status());verifyNoInteractions(repository,ai);}
    @Test void parallelDuplicateIsRejectedAndGateIsReleased(){when(ai.review(evidence)).thenAnswer(call->{assertEquals(429,assertThrows(AppError.class,()->service.review(session,100,true)).status());return new AiSelection(List.of("HISTORY"),List.of("CONFIRM_PURPOSE"));});assertEquals("AI_ASSISTED",service.review(session,100,true).mode());assertEquals("EVIDENCE_ONLY",service.review(session,100,false).mode());}
}
