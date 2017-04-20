package io.sentry.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.core.BasicStatusManager;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.status.OnConsoleStatusListener;
import mockit.*;
import io.sentry.Sentry;
import io.sentry.SentryFactory;
import io.sentry.dsn.Dsn;
import io.sentry.environment.SentryEnvironment;
import io.sentry.event.Event;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SentryAppenderFailuresTest {
    @Injectable
    private Sentry mockSentry = null;
    @Injectable
    private Context mockContext = null;
    @SuppressWarnings("unused")
    @Mocked("sentryInstance")
    private SentryFactory mockSentryFactory;

    @BeforeMethod
    public void setUp() throws Exception {
        new MockUpStatusPrinter();
        new NonStrictExpectations() {{
            final BasicStatusManager statusManager = new BasicStatusManager();
            final OnConsoleStatusListener listener = new OnConsoleStatusListener();
            listener.start();
            statusManager.add(listener);

            mockContext.getStatusManager();
            result = statusManager;
        }};
    }

    @Test
    public void testSentryFailureDoesNotPropagate() throws Exception {
        final SentryAppender sentryAppender = new SentryAppender(mockSentry);
        sentryAppender.setContext(mockContext);
        sentryAppender.setMinLevel("ALL");
        new NonStrictExpectations() {{
            mockSentry.sendEvent((Event) any);
            result = new UnsupportedOperationException();
        }};
        sentryAppender.start();

        sentryAppender.append(new MockUpLoggingEvent(null, null, Level.INFO, null, null, null).getMockInstance());

        new Verifications() {{
            mockSentry.sendEvent((Event) any);
        }};
        assertThat(mockContext.getStatusManager().getCount(), is(1));
    }

    @Test
    public void testSentryFactoryFailureDoesNotPropagate() throws Exception {
        final String dsnUri = "proto://private:public@host/1";
        final SentryAppender sentryAppender = new SentryAppender();
        sentryAppender.setContext(mockContext);
        sentryAppender.setDsn(dsnUri);
        new Expectations() {{
            SentryFactory.sentryInstance(withEqual(new Dsn(dsnUri)), anyString);
            result = new UnsupportedOperationException();
        }};
        sentryAppender.start();

        sentryAppender.initSentry();

        assertThat(mockContext.getStatusManager().getCount(), is(1));
    }

    @Test
    public void testAppendFailIfCurrentThreadSpawnedBySentry() throws Exception {
        SentryEnvironment.startManagingThread();
        try {
            final SentryAppender sentryAppender = new SentryAppender(mockSentry);
            sentryAppender.setContext(mockContext);
            sentryAppender.start();

            sentryAppender.append(new MockUpLoggingEvent(null, null, Level.INFO, null, null, null).getMockInstance());

            new Verifications() {{
                mockSentry.sendEvent((Event) any);
                times = 0;
            }};
            assertThat(mockContext.getStatusManager().getCount(), is(0));
        } finally {
            SentryEnvironment.stopManagingThread();
        }
    }
}