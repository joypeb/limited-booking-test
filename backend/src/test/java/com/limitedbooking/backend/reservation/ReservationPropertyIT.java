package com.limitedbooking.backend.reservation;

import com.limitedbooking.backend.resource.ResourceService;
import com.limitedbooking.backend.support.ContainerTestSupport;
import com.limitedbooking.backend.support.ReferenceReservationModel;
import com.limitedbooking.backend.support.ReservationCommand;
import com.limitedbooking.backend.support.ReservationCommandRunner;
import com.limitedbooking.backend.support.TestData;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.quicktheories.core.Gen;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.AbstractTestExecutionListener;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.quicktheories.QuickTheory.qt;
import static org.quicktheories.generators.SourceDSL.integers;
import static org.quicktheories.generators.SourceDSL.lists;

/**
 * Redis·HTTP·인증을 우회한 내부 서비스의 순차 정합성 검증이다.
 * 동시 실행 교차는 ReservationConcurrencyIT의 책임이다.
 */
@Tag("property")
@DirtiesContext
@TestExecutionListeners(listeners = ReservationPropertyIT.Cleanup.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ReservationPropertyIT {
    static final ContainerTestSupport containers = ContainerTestSupport.postgresOnly();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        containers.registerLocalAuthAndDatabaseProperties(properties);
    }

    public static class Cleanup extends AbstractTestExecutionListener {
        @Override
        public int getOrder() {
            return 2999;
        }

        @Override
        public void afterTestClass(TestContext context) {
            containers.close();
        }
    }

    @Autowired ReservationService reservations;
    @Autowired ResourceService resources;
    @Autowired DataSource dataSource;

    @Test
    void sequentialCommandsMatchIndependentModel() throws NoSuchAlgorithmException {
        long seed = Long.parseLong(System.getProperty("QT_SEED", "20261023"));
        var attempts = new AtomicInteger();
        var digest = MessageDigest.getInstance("SHA-256");
        var commands = lists().of(commands()).ofSizeBetween(1, 30).describedAs(Object::toString);

        System.out.println("reservation-property seed=" + seed + " examples=150");
        try {
            qt().withFixedSeed(seed).withExamples(150).withShrinkCycles(200)
                    .forAll(integers().between(0, 5), integers().between(0, 5), commands)
                    .checkAssert((capacityR1, capacityR2, sequence) -> {
                        attempts.incrementAndGet();
                        String scenario = "Scenario(" + capacityR1 + "," + capacityR2 + "," + sequence + ")";
                        digest.update((scenario + "\n").getBytes(StandardCharsets.UTF_8));

                        // 축소 재실행에도 DB와 실행기 대응표를 새로 준비한다.
                        new TestData(dataSource).reset();
                        var runner = new ReservationCommandRunner(reservations, resources, dataSource);
                        runner.prepare(capacityR1, capacityR2);
                        var model = new ReferenceReservationModel(capacityR1, capacityR2);
                        for (int i = 0; i < sequence.size(); i++) {
                            ReservationCommand command = sequence.get(i);
                            assertThat(runner.run(command))
                                    .as("명령 #%d %s, 시나리오 %s", i, command, scenario)
                                    .isEqualTo(model.apply(command));
                        }
                        runner.assertFinalStateMatches(model);
                    });
            assertThat(attempts.get()).as("실제 속성 실행 횟수").isGreaterThanOrEqualTo(100);
        } finally {
            System.out.println("reservation-property attempts=" + attempts.get() + " seed=" + seed
                    + " scenario-digest=" + HexFormat.of().formatHex(digest.digest()));
        }
    }

    private static Gen<ReservationCommand> commands() {
        Gen<ReservationCommand.User> users = integers().between(0, 1)
                .map(index -> ReservationCommand.User.values()[index]);
        Gen<ReservationCommand.Target> targets = integers().between(0, 7).map(index -> {
            if (index < 4) return ReservationCommand.Target.R1;
            if (index < 7) return ReservationCommand.Target.R2;
            return ReservationCommand.Target.MISSING;
        });
        Gen<ReservationCommand.Key> keys = integers().between(0, 2)
                .map(index -> ReservationCommand.Key.values()[index]);
        Gen<Integer> refs = integers().between(0, 9);
        Gen<ReservationCommand> creates = users.zip(targets, keys, ReservationCommand.Create::new);
        Gen<ReservationCommand> gets = users.zip(refs, ReservationCommand.Get::new);
        Gen<ReservationCommand> cancels = users.zip(refs, ReservationCommand.Cancel::new);
        // Create 3/5, Get 1/5, Cancel 1/5. Create 중 MISSING은 1/8이다.
        return integers().between(0, 4).flatMap(kind -> {
            if (kind < 3) return creates;
            return kind == 3 ? gets : cancels;
        }).describedAs(Object::toString);
    }
}
