package com.limitedbooking.backend.support;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 실제 루트 로그의 메시지·인자·예외를 스레드 안전하게 수집한다. */
public final class LogCapture implements AutoCloseable {
    private final Logger root;
    private final ListAppender<ILoggingEvent> appender;

    private LogCapture(Logger root) {
        this.root = root;
        appender = new ListAppender<>();
        appender.list = new CopyOnWriteArrayList<>();
        appender.setContext(root.getLoggerContext());
        appender.start();
        root.addAppender(appender);
    }

    public static LogCapture attachToRoot() {
        return new LogCapture((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME));
    }

    public List<String> renderedEvents() {
        return List.copyOf(appender.list).stream().map(event -> {
            var rendered = new StringBuilder(String.valueOf(event.getFormattedMessage()));
            var arguments = event.getArgumentArray();
            if (arguments != null) {
                for (Object argument : arguments) rendered.append('\n').append(String.valueOf(argument));
            }
            if (event.getThrowableProxy() != null) {
                rendered.append('\n').append(ThrowableProxyUtil.asString(event.getThrowableProxy()));
            }
            return rendered.toString();
        }).toList();
    }

    public int size() {
        return appender.list.size();
    }

    @Override public void close() {
        root.detachAppender(appender);
        appender.stop();
    }
}
