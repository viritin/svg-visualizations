package org.vaadin.svgvis.unit;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.internal.MockVaadin;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.dom.Element;
import org.junit.jupiter.api.Test;
import org.vaadin.svgvis.SvgSparkLine;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What draw() produces, and when the chart draws itself. Colours are set as
 * write-only attributes that only the browser sees, so they are checked in the
 * browser; the rest is visible here.
 */
public class SvgSparkLineDrawingTest extends BrowserlessTest {

    private UI ui() {
        return UI.getCurrent();
    }

    /** A round trip, which runs what Vaadin runs just before a response. */
    private void respond() {
        MockVaadin.clientRoundtrip();
    }

    private static Optional<Element> part(SvgSparkLine chart, String className) {
        return chart.getElement().getChildren()
                .filter(e -> e.getClassList().contains(className)).findFirst();
    }

    @Test
    public void thePartsAreNamedAndTheGridIsFaint() {
        SvgSparkLine chart = new SvgSparkLine(100);
        chart.setData(1, 3, 2);
        chart.setTitle("Temperature");
        chart.draw();

        Element line = part(chart, "sparkline-line").orElseThrow();
        assertEquals("non-scaling-stroke", line.getAttribute("vector-effect"));
        assertEquals("0.25", part(chart, "sparkline-grid").orElseThrow().getAttribute("stroke-opacity"));
        assertEquals("0.7", part(chart, "sparkline-label").orElseThrow().getAttribute("fill-opacity"));
        assertTrue(part(chart, "sparkline-title").isPresent());
    }

    @Test
    public void theScaleIsTheSameInEveryLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("fi-FI"));
            SvgSparkLine chart = new SvgSparkLine(100);
            chart.setData(20.25, 21.5);
            chart.draw();
            List<String> labels = chart.getElement().getChildren()
                    .filter(e -> e.getClassList().contains("sparkline-label"))
                    .map(Element::getText).toList();
            assertTrue(labels.contains("21.5"), labels.toString());
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    public void changesDrawThemselvesOncePerResponse() {
        SvgSparkLine chart = new SvgSparkLine(100);
        ui().add(chart);
        chart.setData(1, 3, 2);
        chart.setTitle("Temperature");
        assertEquals(0, chart.getElement().getChildCount(), "nothing drawn before the response");

        respond();
        assertTrue(part(chart, "sparkline-line").isPresent());
        assertTrue(part(chart, "sparkline-title").isPresent(), "the title set after the data is in the same drawing");
    }

    /** The data is gone after drawing; redrawing for a new title would leave an empty chart. */
    @Test
    public void aSettingChangedAfterDrawingDoesNotWipeTheChart() {
        SvgSparkLine chart = new SvgSparkLine(100);
        ui().add(chart);
        chart.setData(1, 3, 2);
        respond();

        chart.setTitle("Later");
        respond();
        assertTrue(part(chart, "sparkline-line").isPresent());

        chart.setData(4, 5, 6);
        respond();
        assertTrue(part(chart, "sparkline-title").isPresent(), "the title shows with the next data");
    }

    /** Once, every attach drew again with the dropped data, wiping a chart that was moved. */
    @Test
    public void aChartSurvivesBeingDetachedAndAttachedAgain() {
        SvgSparkLine chart = new SvgSparkLine(100);
        ui().add(chart);
        chart.setData(1, 3, 2);
        respond();

        ui().remove(chart);
        ui().add(chart);
        respond();
        assertTrue(part(chart, "sparkline-line").isPresent());
    }

    /** Humidity and pressure in one chart: each against its own scale, labelled at its own edge. */
    @Test
    public void aSeriesWithItsOwnScaleIsLabelledAtTheRight() {
        SvgSparkLine chart = new SvgSparkLine(100);
        chart.setData(40, 45, 50);
        chart.setUnit(" % RH");
        chart.addSeriesWithOwnScale(List.of(SvgSparkLine.DataPoint.of(0, 1009.8), SvgSparkLine.DataPoint.of(1, 1011.4)),
                null, " hPa");
        chart.draw();

        List<String> left = labels(chart, "sparkline-line-label");
        List<String> right = spans(chart, "sparkline-series-label");
        assertEquals(List.of("40.0 % RH", "50.0 % RH"), left, "the primary scale, not stretched by the pressure");
        assertEquals(List.of("1009.8 hPa", "1011.4 hPa"), right);
    }

    /** Gaps are opt in: off, the curve runs through; on, it breaks and a dashed line bridges it. */
    @Test
    public void gapsAreBridgedOnlyWhenAskedFor() {
        List<SvgSparkLine.DataPoint> gappy = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) gappy.add(SvgSparkLine.DataPoint.of(i, i % 3));
        for (int i = 0; i < 10; i++) gappy.add(SvgSparkLine.DataPoint.of(500 + i, i % 3));

        SvgSparkLine plain = new SvgSparkLine(100);
        plain.setData(gappy);
        plain.draw();
        assertTrue(part(plain, "sparkline-gap").isEmpty());

        SvgSparkLine withGaps = new SvgSparkLine(100);
        withGaps.setShowGaps(true);
        withGaps.setData(gappy);
        withGaps.draw();
        assertTrue(part(withGaps, "sparkline-gap").isPresent());
    }

    /** Four lines in one chart: every own scale gets its numbers, side by side at the right. */
    @Test
    public void everySeriesWithItsOwnScaleIsLabelled() {
        SvgSparkLine chart = new SvgSparkLine(100);
        chart.setData(40, 50);
        chart.addSeriesWithOwnScale(List.of(SvgSparkLine.DataPoint.of(0, 1000), SvgSparkLine.DataPoint.of(1, 1010)), null, " hPa");
        chart.addSeriesWithOwnScale(List.of(SvgSparkLine.DataPoint.of(0, 2.9), SvgSparkLine.DataPoint.of(1, 3.1)), null, " V");
        chart.addSeriesWithOwnScale(List.of(SvgSparkLine.DataPoint.of(0, -60), SvgSparkLine.DataPoint.of(1, -40)), null, " dBm");
        chart.draw();

        assertEquals(List.of("1000.0 hPa", "2.9 V", "-60.0 dBm", "1010.0 hPa", "3.1 V", "-40.0 dBm"),
                spans(chart, "sparkline-series-label"), "the bottom row, then the top row");
        assertEquals(List.of("3.1 V"), spans(chart, "sparkline-series-label-3").subList(1, 2),
                "each series' labels are numbered like its line");
        assertTrue(part(chart, "sparkline-series-4").isPresent());
    }

    /** The texts of the spans with the class, row by row. */
    private static List<String> spans(SvgSparkLine chart, String className) {
        return chart.getElement().getChildren()
                .flatMap(Element::getChildren)
                .filter(e -> e.getClassList().contains(className))
                .map(Element::getText).toList();
    }

    private static List<String> labels(SvgSparkLine chart, String className) {
        return chart.getElement().getChildren()
                .filter(e -> e.getClassList().contains(className))
                .map(Element::getText).toList();
    }
}
