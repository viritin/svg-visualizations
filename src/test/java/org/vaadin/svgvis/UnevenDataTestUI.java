package org.vaadin.svgvis;

import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Data that is not evenly spaced, the way a battery sensor reports: a dense
 * burst of noisy readings, a long silence, and a few readings again as the
 * temperature starts to climb. The curve should neither loop back nor bridge the
 * silence with an invented arc, and it should end at the latest reading.
 */
@Route
public class UnevenDataTestUI extends VerticalLayout {

    public UnevenDataTestUI() {
        setMaxWidth("40rem");
        add(new H3("Uneven data"));
        add(new Paragraph("Dense start, a gap of most of a day, then a short rise at the end."));
        add(chart(unevenReadings()));
        add(new Paragraph("A reading every minute for a day, rising only in the last 20 minutes."));
        add(chart(lateRise()));
        add(new Paragraph("A noisy sensor over a day, as the default moving average draws it..."));
        add(chart(noisyDay()));
        add(new Paragraph("...and with LTTB, which keeps the noise's extremes."));
        var lttb = chart(noisyDay());
        lttb.setSmoothing(SvgSparkLine.Smoothing.LTTB);
        add(lttb);
    }

    /** A cellar's day: a slow dip and recovery under ±0.2 °C of sensor noise. */
    static List<SvgSparkLine.DataPoint> noisyDay() {
        Instant start = Instant.parse("2026-09-26T15:55:00Z");
        List<SvgSparkLine.DataPoint> data = new ArrayList<>();
        var random = new java.util.Random(4052);
        for (int i = 0; i < 24 * 60; i++) {
            double t = i / (24.0 * 60);
            double y = 16.5 - 4 * Math.exp(-Math.pow((t - 0.6) / 0.15, 2)) + random.nextGaussian() * 0.2;
            data.add(SvgSparkLine.DataPoint.of(start.plus(Duration.ofMinutes(i)), y));
        }
        return data;
    }

    private static SvgSparkLine chart(List<SvgSparkLine.DataPoint> data) {
        var chart = new SvgSparkLine(100);
        chart.setShowGaps(true);
        chart.setTitle("Temperature °C");
        chart.setData(data);
        chart.setTimeScale("start", "now");
        return chart;
    }

    static List<SvgSparkLine.DataPoint> unevenReadings() {
        Instant start = Instant.parse("2026-09-17T13:23:00Z");
        List<SvgSparkLine.DataPoint> data = new ArrayList<>();
        // A burst of noisy readings every 20 seconds while the sensor warms up
        for (int i = 0; i < 40; i++) {
            double y = 22.9 + i * 0.012 + ((i * 7919) % 5) * 0.03;
            data.add(SvgSparkLine.DataPoint.of(start.plus(Duration.ofSeconds(20L * i)), y));
        }
        // Silence for most of a day, then a handful of readings as it warms up
        Instant later = start.plus(Duration.ofHours(25));
        double[] tail = {23.35, 23.3, 23.28, 23.34, 23.42, 23.5};
        for (int i = 0; i < tail.length; i++) {
            data.add(SvgSparkLine.DataPoint.of(later.plus(Duration.ofMinutes(4L * i)), tail[i]));
        }
        return data;
    }

    static List<SvgSparkLine.DataPoint> lateRise() {
        Instant start = Instant.parse("2026-09-17T12:00:00Z");
        List<SvgSparkLine.DataPoint> data = new ArrayList<>();
        int minutes = 24 * 60;
        for (int i = 0; i < minutes; i++) {
            double y = 21 + Math.sin(i / 90.0) * 0.3;
            if (i > minutes - 20) {
                y += (i - (minutes - 20)) * 0.08;
            }
            data.add(SvgSparkLine.DataPoint.of(start.plus(Duration.ofMinutes(i)), y));
        }
        return data;
    }
}
