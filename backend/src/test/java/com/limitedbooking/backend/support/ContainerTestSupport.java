package com.limitedbooking.backend.support;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;

/** 이번 시험이 만든 컨테이너만 소유한다. 개발 DB/volume은 사용하지 않는다. */
public final class ContainerTestSupport implements AutoCloseable {
    public static final String POSTGRES_IMAGE = "postgres:18.6-bookworm@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c";
    public static final String REDIS_IMAGE = "redis:8.10.2-trixie@sha256:2c2dff791878316e3b083188be0d578423b24813cdda5de0e80ea4f7b3e6bfd6";
    public final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE).asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("toolchain_" + UUID.randomUUID().toString().replace("-", ""))
            .withUsername("toolchain").withPassword(UUID.randomUUID().toString())
            .withStartupTimeout(Duration.ofSeconds(120));
    public final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
            .withExposedPorts(6379).withStartupTimeout(Duration.ofSeconds(120));

    public ContainerTestSupport() {
        try { postgres.start(); redis.start(); }
        catch (RuntimeException failure) {
            try { close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    @Override public void close() {
        RuntimeException failure = null;
        for (var container : new GenericContainer<?>[]{redis,postgres}) {
            var executor = Executors.newSingleThreadExecutor(r -> {
                var thread = new Thread(r,"toolchain-cleanup");thread.setDaemon(true);return thread;
            });
            try {
                executor.submit(container::stop).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                var error = new IllegalStateException("시험 자원 정리 실패 container=" + container.getContainerId(),e);
                if (failure==null) failure=error; else failure.addSuppressed(error);
            } finally { executor.shutdownNow(); }
        }
        if (failure!=null) throw failure;
    }
}
