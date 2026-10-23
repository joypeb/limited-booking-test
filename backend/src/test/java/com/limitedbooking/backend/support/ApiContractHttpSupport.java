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
        return sendFramed(method,path,headers,body,false);
    }
    public ApiContractValidator.HttpExchange sendFramed(String method,String path,Map<String,String> headers,String body,boolean chunked) throws Exception {
        guard();
        String id=UUID.randomUUID().toString(); submitted.add(id);
        byte[] payload=body==null?new byte[0]:body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        observation.intended(id,payload.length);
        var builder=HttpRequest.newBuilder(base.resolve(path)).version(HttpClient.Version.HTTP_1_1).timeout(Duration.ofSeconds(9))
                .header("X-Test-Request-Id",id);
        headers.forEach(builder::header);
        var source=body==null?HttpRequest.BodyPublishers.noBody():chunked?
                HttpRequest.BodyPublishers.ofInputStream(()->new java.io.ByteArrayInputStream(payload)):
                HttpRequest.BodyPublishers.ofByteArray(payload);
        HttpRequest.BodyPublisher publisher=new HttpRequest.BodyPublisher() {
            @Override public long contentLength(){return source.contentLength();}
            @Override public void subscribe(java.util.concurrent.Flow.Subscriber<? super java.nio.ByteBuffer> subscriber) {
                source.subscribe(new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
                    @Override public void onSubscribe(java.util.concurrent.Flow.Subscription subscription){subscriber.onSubscribe(subscription);}
                    @Override public void onNext(java.nio.ByteBuffer buffer){observation.published(id,buffer.remaining());subscriber.onNext(buffer);}
                    @Override public void onError(Throwable error){subscriber.onError(error);}
                    @Override public void onComplete(){subscriber.onComplete();}
                });
            }
        };
        var request=builder.method(method,publisher).build();
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
    public record Transport(int intendedBytes,int publishedBytes,int receivedBytes,long contentLength,String transferEncoding) { }
    public Transport transport(ApiContractValidator.HttpExchange exchange) throws InterruptedException {
        guard();
        String id=exchange.requestHeaders().entrySet().stream().filter(e->e.getKey().equalsIgnoreCase("X-Test-Request-Id"))
                .map(Map.Entry::getValue).findFirst().orElseThrow();
        return observation.transport(id);
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
        private static final class Transfer {
            volatile int intended;
            final java.util.concurrent.atomic.AtomicInteger published=new java.util.concurrent.atomic.AtomicInteger();
            final java.util.concurrent.atomic.AtomicInteger received=new java.util.concurrent.atomic.AtomicInteger();
            volatile long length=-1;volatile String encoding;
        }
        private final java.util.concurrent.ConcurrentMap<String,Transfer> transfers=new java.util.concurrent.ConcurrentHashMap<>();
        private Transfer transfer(String id){return transfers.computeIfAbsent(id,key->new Transfer());}
        public void intended(String id,int bytes){transfer(id).intended=bytes;}
        public void published(String id,int bytes){transfer(id).published.addAndGet(bytes);}
        public void receiving(String id,long length,String encoding){var t=transfer(id);t.length=length;t.encoding=encoding;}
        public void read(String id,int bytes){transfer(id).received.addAndGet(bytes);}
        public Transport transport(String id){var t=transfer(id);return new Transport(t.intended,t.published.get(),t.received.get(),t.length,t.encoding);}
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
