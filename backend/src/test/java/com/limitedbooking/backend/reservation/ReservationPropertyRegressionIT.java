package com.limitedbooking.backend.reservation;

import com.limitedbooking.backend.resource.ResourceService;
import com.limitedbooking.backend.support.ContainerTestSupport;
import com.limitedbooking.backend.support.ReferenceReservationModel;
import com.limitedbooking.backend.support.ReservationCommand;
import com.limitedbooking.backend.support.ReservationCommandRunner;
import com.limitedbooking.backend.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.AbstractTestExecutionListener;

import javax.sql.DataSource;
import java.util.List;

import static com.limitedbooking.backend.support.ReservationCommand.Key.K1;
import static com.limitedbooking.backend.support.ReservationCommand.Target.R1;
import static com.limitedbooking.backend.support.ReservationCommand.Target.R2;
import static com.limitedbooking.backend.support.ReservationCommand.User.A;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 10단계 속성 검증의 축소 최소 사례.
 * 고의 변경 D(재요청 대상 비교 제거), seed 20261023, QuickTheories 0.26 shrinkCycles 2000.
 * 실제 구현 결함이 아닌 학습용 고의 결함으로 얻은 사례.
 */
@DirtiesContext
@TestExecutionListeners(listeners = ReservationPropertyRegressionIT.Cleanup.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ReservationPropertyRegressionIT {
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
    void sameUserAndKeyOnAnotherTargetConflictsEvenWhenOtherTargetIsSoldOut() {
        new TestData(dataSource).reset();
        var runner = new ReservationCommandRunner(reservations, resources, dataSource);
        runner.prepare(1, 0);
        var model = new ReferenceReservationModel(1, 0);
        List<ReservationCommand> commands = List.of(
                new ReservationCommand.Create(A, R1, K1),
                new ReservationCommand.Create(A, R2, K1));

        // 첫 명령은 CREATED, 두 번째는 IDEMPOTENCY_CONFLICT다.
        // 최종 R1 available=0, R2 available=0, 예약은 R1의 1건만 남아야 한다.
        for (int i = 0; i < commands.size(); i++) {
            ReservationCommand command = commands.get(i);
            assertThat(runner.run(command))
                    .as("명령 #%d %s, 축소 시나리오 Scenario(1,0,%s)", i, command, commands)
                    .isEqualTo(model.apply(command));
        }
        runner.assertFinalStateMatches(model);
    }
}
