package com.yammer.metrics.ganglia;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.*;
import com.yammer.metrics.reporting.AbstractPollingReporter;
import com.yammer.metrics.reporting.tests.AbstractPollingReporterTest;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.SocketException;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GangliaReporterTest extends AbstractPollingReporterTest {

    @Override
    protected AbstractPollingReporter createReporter(MetricRegistry registry, OutputStream out, Clock clock) throws Exception {
        final OutputStreamWriter output = new OutputStreamWriter(out);
        final GangliaMessage testMessage = new GangliaMessage(null, null, null) {

            @Override
            public GangliaMessage addInt(int value) {
                try {
                    output.append("addInt(" + value + ")\n").flush();
                } catch (IOException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
                return this;
            }

            @Override
            public GangliaMessage addString(String value) {
                try {
                    output.append("addString(" + value + ")\n").flush();
                } catch (IOException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
                return this;
            }

            @Override
            public void send() throws SocketException, IOException {
                output.append("send()\n").flush();
            }

            @Override
            public String toString() {
                return output.toString();
            }
        };

        final GangliaMessageBuilder messageBuilder = mock(GangliaMessageBuilder.class);
        when(messageBuilder.newMessage()).thenReturn(testMessage);

        final GangliaReporter reporter = new GangliaReporter(registry,
                                                             "group-prefix",
                                                             MetricPredicate.ALL,
                                                             false,
                                                             TimeUnit.MILLISECONDS,
                                                             messageBuilder,
                                                             VirtualMachineMetrics.getInstance()) {
            @Override
            String getDefaultHostLabel() {
                return "localhost";
            }

            @Override
            public void run() {
                super.run();
            }
        };
        reporter.printVMMetrics = false;
        return reporter;
    }

    @Test
    public void testSanitizeName_noBadCharacters() throws IOException {
        final String metricName = Metrics.name("thisIs", "AClean", "Metric");
        final GangliaReporter gangliaReporter = new GangliaReporter("localhost", 5555);
        final String cleanMetricName = gangliaReporter.sanitizeName(metricName);
        assertEquals("clean metric name was changed unexpectedly",
                     "thisIs.AClean.Metric",
                     cleanMetricName);
    }

    @Test
    public void testSanitizeName_badCharacters() throws IOException {
        final String metricName = Metrics.name("thisIs", "AC>&!>lean", "Metric Name");
        final String expectedMetricName = "thisIs.AC____lean.Metric_Name";
        final GangliaReporter gangliaReporter = new GangliaReporter("localhost", 5555);
        final String cleanMetricName = gangliaReporter.sanitizeName(metricName);
        assertEquals("clean metric name did not match expected value",
                     expectedMetricName,
                     cleanMetricName);
    }

    @Test
    public void testCompressPackageName() throws IOException {
        final String metricName = Metrics.name("some.long.package.name.thisIs",
                                               "AC>&!>lean",
                                               "Metric Name");
        final String expectedMetricName = "s.l.p.n.t.AC____lean.Metric_Name";
        final GangliaReporter gangliaReporter = new GangliaReporter("localhost", 5555, true);
        final String cleanMetricName = gangliaReporter.sanitizeName(metricName);
        assertEquals("clean metric name did not match expected value",
                     expectedMetricName,
                     cleanMetricName);
    }

    protected String getFromFile(String fileName) {
        try {
            return IOUtils.toString(new FileInputStream(getClass().getClassLoader()
                                                                .getResource(fileName)
                                                                .getFile()));
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public String[] expectedGaugeResult(String value) {
        return String.format(getFromFile("gauge.io"), value).split("\\n");
    }

    @Override
    public String[] expectedTimerResult() {
        return getFromFile("timed.io").split("\\n");
    }

    @Override
    public String[] expectedMeterResult() {
        return getFromFile("metered.io").split("\\n");
    }

    @Override
    public String[] expectedHistogramResult() {
        return getFromFile("histogram.io").split("\\n");
    }

    @Override
    public String[] expectedCounterResult(long count) {
        return String.format(getFromFile("counter.io"), count).split("\\n");
    }
}
