package com.limitedbooking.backend.support;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** 실제 전송과 원문 capture만 담당한다. 소유 DB/Redis 준비는 guarded 단계로 전달한다. */
public final class ApiContractHttpSupport implements AutoCloseable {
    private final URI base;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ConcurrencyTestSupport.StopState stop;
    private final ServerObservation observation;
    private final Duration deadline;
    private final Set<String> submitted=new HashSet<>();
    private Throwable firstFailure;
    private boolean closed;
    public ApiContractHttpSupport(int port, ServerObservation observation, ConcurrencyTestSupport.StopState stop) {
        this(port,observation,stop,Duration.ofSeconds(10));
    }
    public ApiContractHttpSupport(int port, ServerObservation observation, ConcurrencyTestSupport.StopState stop, Duration deadline) {
        this.base=URI.create("http://127.0.0.1:"+port); this.observation=observation; this.stop=stop; this.deadline=deadline;
    }
    public int submittedCount() { return submitted.size(); }
    public void reset(Runnable reset) { guard(); reset.run(); }
    public void prepare(Runnable prepare) { guard(); prepare.run(); }
    private void guard() {
        stop.check();
        if(closed) throw new IllegalStateException("종료된 HTTP 지원");
        try { observation.await(submitted,deadline); }
        catch(InterruptedException e) {
            Thread.currentThread().interrupt(); var failure=new IllegalStateException("server 종료 대기 인터럽트",e);
            failed(failure);throw failure;
        } catch(RuntimeException e) { failed(e);throw e; }
    }
    private void failed(Throwable failure) {
        firstFailure=ConcurrencyTestSupport.preserve(firstFailure,failure);
        stop.stop(firstFailure);
    }
    public ApiContractValidator.HttpExchange send(String method,String path,Map<String,String> headers,String body) throws Exception {
        guard();
        String id=UUID.randomUUID().toString(); submitted.add(id);
        var builder=HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(9))
                .header("X-Test-Request-Id",id);
        headers.forEach(builder::header);
        var request=builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build();
        try {
            var response=HttpClientTestSupport.send(client,request);
            return new ApiContractValidator.HttpExchange(method,request.uri().getRawPath(),
                    ApiContractValidator.singletonHeaders(request.headers().map()),body,response.statusCode(),
                    ApiContractValidator.singletonHeaders(response.headers().map()),response.body());
        } catch(Exception | Error failure) {
            if(failure instanceof InterruptedException) Thread.currentThread().interrupt();
            failed(failure);throw failure;
        }
    }
    @Override public void close() throws Exception {
        if(closed) return;
        closed=true;
        Throwable cleanup=null;
        try { HttpClientTestSupport.close(client); }
        catch(Exception | Error failure) {
            if(failure instanceof InterruptedException) Thread.currentThread().interrupt();
            cleanup=ConcurrencyTestSupport.preserve(cleanup,failure);
        }
        try { observation.await(submitted,deadline); }
        catch(Exception | Error failure) {
            if(failure instanceof InterruptedException) Thread.currentThread().interrupt();
            cleanup=ConcurrencyTestSupport.preserve(cleanup,failure);
        }
        // send의 예외를 다시 던지면 try-with-resources가 자기 억제를 시도한다.
        // 종료에서 새로 발생한 오류만 던지고 StopState의 최초 원인은 유지한다.
        if(cleanup!=null) {
            boolean priorFailure=firstFailure!=null;
            failed(cleanup);
            if(priorFailure) throw new IllegalStateException("HTTP 종료 실패",cleanup);
            if(cleanup instanceof Exception e) throw e;
            if(cleanup instanceof Error e) throw e;
        }
    }
    public static final class ServerObservation {
        private final Set<String> completed=java.util.concurrent.ConcurrentHashMap.newKeySet();
        private final java.util.concurrent.atomic.AtomicInteger active=new java.util.concurrent.atomic.AtomicInteger();
        public void entered() { active.incrementAndGet(); }
        public void completed(String id) { completed.add(id); active.decrementAndGet(); }
        public boolean idle(Set<String> ids) { return active.get()==0 && completed.containsAll(ids); }
        public void await(Set<String> ids,Duration timeout) throws InterruptedException {
            long end=System.nanoTime()+timeout.toNanos();
            while(!idle(ids)) {
                if(System.nanoTime()>=end) throw new IllegalStateException("server in-flight 종료 불확실");
                Thread.sleep(5);
            }
        }
    }
}
