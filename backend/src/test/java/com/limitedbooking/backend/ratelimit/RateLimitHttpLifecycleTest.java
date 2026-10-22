package com.limitedbooking.backend.ratelimit;

import com.limitedbooking.backend.support.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import javax.sql.DataSource;
import java.io.IOException;
import java.net.http.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 통합 장애의 대체가 아니라 종료·전송 불확실 후 지원 코드의 차단을 검사하는 fixture다. */
class RateLimitHttpLifecycleTest {
    static class Fixture extends RateLimitHttpTestSupport {
        final ConcurrencyTestSupport.StopState ownState=new ConcurrencyTestSupport.StopState();
        RuntimeException serverFailure,closeFailure;
        Fixture(){dataSource=mock(DataSource.class);redis=mock(StringRedisTemplate.class);data=mock(TestData.class);client=mock(HttpClient.class);port=1;}
        @Override ConcurrencyTestSupport.StopState stopState(){return ownState;}
        @Override void quiescent(){if(serverFailure!=null)throw serverFailure;}
        @Override void closeHttp(){if(closeFailure!=null)throw closeFailure;}
    }
    void blocked(Fixture fixture,Throwable first){
        assertThatThrownBy(fixture::setupHttp).hasCause(first);
        assertThatThrownBy(()->fixture.resource(1)).hasCause(first);
        assertThatThrownBy(()->fixture.seed(UUID.randomUUID(),TestData.USER_A_ID,"seed")).hasCause(first);
        assertThatThrownBy(()->fixture.create(UUID.randomUUID(),"next")).hasCause(first);
        verifyNoInteractions(fixture.dataSource,fixture.redis,fixture.data,fixture.client);
    }
    @Test void preservesEveryCleanupErrorAndBlocksRedisDatabasePreparationAndSubmission(){
        var fixture=new Fixture();
        var first=new IllegalStateException("서버 종료 실패");var second=new IllegalStateException("HTTP 종료 실패");
        fixture.serverFailure=first;fixture.closeFailure=second;
        assertThatThrownBy(fixture::cleanupHttp).isSameAs(first);
        assertThat(first.getSuppressed()).containsExactly(second);
        blocked(fixture,first);
        fixture.ownState.stop(new IllegalStateException("나중 오류"));blocked(fixture,first);
    }
    @Test void uncertainHttpTransportBlocksEveryNextResetAndPreservesFirstCause(){
        var fixture=new Fixture();var failure=new IOException("전송 결과 불확실");
        doReturn(CompletableFuture.failedFuture(failure)).when(fixture.client).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        assertThatThrownBy(()->fixture.create(UUID.randomUUID(),"uncertain")).isSameAs(failure);
        clearInvocations(fixture.client);
        blocked(fixture,failure);
    }
}
