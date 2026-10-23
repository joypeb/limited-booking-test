package com.limitedbooking.backend.support;

/** 테스트 전용 예약 명령. 사용자·대상·키를 테스트 식별자로 표현한다. */
public sealed interface ReservationCommand
        permits ReservationCommand.Create, ReservationCommand.Get, ReservationCommand.Cancel {

    enum User { A, B }

    enum Target { R1, R2, MISSING }

    enum Key { K1, K2, K3 }

    record Create(User user, Target target, Key key) implements ReservationCommand {
        @Override
        public String toString() {
            return "Create(" + user + "," + target + "," + key + ")";
        }
    }

    record Get(User user, int ref) implements ReservationCommand {
        @Override
        public String toString() {
            return "Get(" + user + "," + ref + ")";
        }
    }

    record Cancel(User user, int ref) implements ReservationCommand {
        @Override
        public String toString() {
            return "Cancel(" + user + "," + ref + ")";
        }
    }
}
