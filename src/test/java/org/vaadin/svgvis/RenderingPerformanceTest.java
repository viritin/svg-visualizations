package org.vaadin.svgvis;

import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.vaadin.flow.component.UI;
import in.virit.color.NamedColor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.vaadin.svgvis.testdata.RawWeatherStationData;
import org.vaadin.svgvis.testdata.WeatherData;

import java.util.List;

/**
 * Test that measures and reports CPU time for rendering/drawing visualizations
 * with different dataset sizes and smoothing algorithms.
 *
 * Uses Karibu Testing to mock Vaadin UI for realistic component rendering.
 * Assertions are disabled via maven-surefire-plugin configuration in pom.xml
 * to work around Vaadin Flow internal assertions with SVG elements.
 *
 * Performance readings are saved to performance-baseline.properties and
 * compared against previous runs. Tests fail if performance degrades by
 * more than 100% (configurable via TOLERANCE constant).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RenderingPerformanceTest {

    private static List<RawWeatherStationData> allData;
    private static final int WARMUP_ITERATIONS = 3;
    private static final int MEASURE_ITERATIONS = 5;

    /** Tolerance for performance regression (1.0 = 100% slower is acceptable) */
    private static final double TOLERANCE = 1.0;

    private static PerformanceBaseline baseline;

    @BeforeAll
    static void loadData() {
        // Set production mode to disable development mode assertions
        System.setProperty("vaadin.productionMode", "true");
        allData = WeatherData.getAll();

        baseline = new PerformanceBaseline();
        baseline.printSystemInfo();

        System.out.println("Loaded " + allData.size() + " weather records for performance testing");
    }

    @AfterAll
    static void saveBaseline() {
        baseline.printSummary();
        baseline.saveBaseline();
    }

    @BeforeEach
    void setupVaadin() {
        MockVaadin.setup();
    }

    @AfterEach
    void tearDownVaadin() {
        MockVaadin.tearDown();
    }

    @Test
    @Order(1)
    void measureSparkLineDrawTime() {
        System.out.println("\n=== SparkLine draw() CPU Time Analysis ===\n");
        System.out.println("Measuring time to execute draw() with real components...\n");

        int[] dataSizes = {100, 1000, 10000, 50000, 100000, 500000, allData.size()};

        System.out.println("Data Points  | No Smoothing   | Moving Avg     | RDP");
        System.out.println("-------------|----------------|----------------|----------------");

        for (int size : dataSizes) {
            if (size > allData.size()) continue;

            List<RawWeatherStationData> subset = allData.subList(allData.size() - size, allData.size());

            List<SvgSparkLine.DataPoint> points = subset.stream()
                    .filter(RawWeatherStationData::hasValidAirTemp)
                    .map(d -> SvgSparkLine.DataPoint.of(d.getInstant(), d.getTempfC()))
                    .toList();

            long noneTime = measureDrawTime(points, SvgSparkLine.Smoothing.NONE);
            long movingAvgTime = measureDrawTime(points, SvgSparkLine.Smoothing.MOVING_AVERAGE);
            long rdpTime = measureDrawTime(points, SvgSparkLine.Smoothing.RDP);

            // Record key measurements for baseline comparison
            baseline.record("sparkline.none." + size, noneTime);
            baseline.record("sparkline.movingAvg." + size, movingAvgTime);
            baseline.record("sparkline.rdp." + size, rdpTime);

            System.out.printf("%,12d | %14s | %14s | %s%n",
                    points.size(), formatTime(noneTime), formatTime(movingAvgTime), formatTime(rdpTime));
        }

        System.out.println("\nNote: Times include full SVG element creation and DOM operations.");
    }

    @Test
    @Order(2)
    void measureWindRoseDrawTime() {
        System.out.println("\n=== WindRose draw() CPU Time Analysis ===\n");
        System.out.println("Measuring time to aggregate and draw WindRose with real components...\n");

        int[] dataSizes = {1000, 10000, 50000, 100000, 500000, allData.size()};

        System.out.println("Input Records | Aggregation    | Draw           | Total");
        System.out.println("--------------|----------------|----------------|----------------");

        for (int size : dataSizes) {
            if (size > allData.size()) continue;

            List<RawWeatherStationData> subset = allData.subList(allData.size() - size, allData.size());

            // Measure aggregation time
            long aggStart = System.nanoTime();
            int sectors = 16;
            double[] duration = new double[sectors];
            double[] energy = new double[sectors];
            for (RawWeatherStationData d : subset) {
                int idx = (int) Math.round(d.getWinddir() / 22.5) % sectors;
                duration[idx]++;
                double speed = d.getWindSpeedMs();
                energy[idx] += speed * speed * speed;
            }
            long aggTime = System.nanoTime() - aggStart;

            // Measure draw time with real component
            long drawTime = measureWindRoseDrawTime(duration, energy, sectors);

            // Record key measurements
            baseline.record("windrose.aggregation." + size, aggTime);
            baseline.record("windrose.draw." + size, drawTime);

            System.out.printf("%,13d | %14s | %14s | %s%n",
                    size, formatTime(aggTime), formatTime(drawTime), formatTime(aggTime + drawTime));
        }

        System.out.println("\nNote: WindRose aggregation is O(n), but draw is O(sectors) = O(1).");
    }

    @Test
    @Order(3)
    void measureScalingBehavior() {
        System.out.println("\n=== Scaling Behavior Analysis ===\n");
        System.out.println("Comparing how draw time scales with data size...\n");

        int baseSize = 10000;
        int[] multipliers = {1, 2, 5, 10, 20, 50};

        // Get baseline times
        List<RawWeatherStationData> baseSubset = allData.subList(allData.size() - baseSize, allData.size());
        List<SvgSparkLine.DataPoint> basePoints = baseSubset.stream()
                .filter(RawWeatherStationData::hasValidAirTemp)
                .map(d -> SvgSparkLine.DataPoint.of(d.getInstant(), d.getTempfC()))
                .toList();

        long baseNone = measureDrawTime(basePoints, SvgSparkLine.Smoothing.NONE);
        long baseMA = measureDrawTime(basePoints, SvgSparkLine.Smoothing.MOVING_AVERAGE);
        long baseRDP = measureDrawTime(basePoints, SvgSparkLine.Smoothing.RDP);

        System.out.println("Multiplier | Data Size    | NONE (rel)     | MovingAvg (rel) | RDP (rel)");
        System.out.println("-----------|--------------|----------------|-----------------|----------------");

        for (int mult : multipliers) {
            int size = baseSize * mult;
            if (size > allData.size()) continue;

            List<RawWeatherStationData> subset = allData.subList(allData.size() - size, allData.size());
            List<SvgSparkLine.DataPoint> points = subset.stream()
                    .filter(RawWeatherStationData::hasValidAirTemp)
                    .map(d -> SvgSparkLine.DataPoint.of(d.getInstant(), d.getTempfC()))
                    .toList();

            long noneTime = measureDrawTime(points, SvgSparkLine.Smoothing.NONE);
            long maTime = measureDrawTime(points, SvgSparkLine.Smoothing.MOVING_AVERAGE);
            long rdpTime = measureDrawTime(points, SvgSparkLine.Smoothing.RDP);

            // Record scaling measurements
            baseline.record("scaling.none." + mult + "x", noneTime);
            baseline.record("scaling.movingAvg." + mult + "x", maTime);
            baseline.record("scaling.rdp." + mult + "x", rdpTime);

            System.out.printf("%10dx | %,12d | %8s (%4.1fx) | %8s (%4.1fx) | %8s (%4.1fx)%n",
                    mult, points.size(),
                    formatTime(noneTime), (double) noneTime / baseNone,
                    formatTime(maTime), (double) maTime / baseMA,
                    formatTime(rdpTime), (double) rdpTime / baseRDP);
        }

        System.out.println("\nIdeal O(n): time should grow linearly with multiplier.");
        System.out.println("With smoothing: time for SVG creation is ~constant after reduction.");
    }

    @Test
    @Order(4)
    void measureOutputSizeVsDrawTime() {
        System.out.println("\n=== Output Size vs Draw Time ===\n");
        System.out.println("Comparing output point count and actual draw time...\n");

        int[] dataSizes = {1000, 10000, 100000, 500000, allData.size()};

        System.out.println("Input Points | MA Elements | MA Draw Time   | RDP Elements | RDP Draw Time");
        System.out.println("-------------|-------------|----------------|--------------|----------------");

        for (int size : dataSizes) {
            if (size > allData.size()) continue;

            List<RawWeatherStationData> subset = allData.subList(allData.size() - size, allData.size());

            List<SvgSparkLine.DataPoint> points = subset.stream()
                    .filter(RawWeatherStationData::hasValidAirTemp)
                    .map(d -> SvgSparkLine.DataPoint.of(d.getInstant(), d.getTempfC()))
                    .toList();

            // Measure MA
            int maElements = countElementsAfterDraw(points, SvgSparkLine.Smoothing.MOVING_AVERAGE);
            long maTime = measureDrawTime(points, SvgSparkLine.Smoothing.MOVING_AVERAGE);

            // Measure RDP
            int rdpElements = countElementsAfterDraw(points, SvgSparkLine.Smoothing.RDP);
            long rdpTime = measureDrawTime(points, SvgSparkLine.Smoothing.RDP);

            System.out.printf("%,12d | %11d | %14s | %12d | %s%n",
                    points.size(), maElements, formatTime(maTime),
                    rdpElements, formatTime(rdpTime));
        }

        System.out.println("\nElement count reflects SVG complexity after smoothing.");
    }

    @Test
    @Order(5)
    void measureMultiSeriesDrawTime() {
        System.out.println("\n=== Multi-Series SparkLine Draw Time ===\n");
        System.out.println("Measuring draw time with multiple data series...\n");

        int dataSize = 100000;
        List<RawWeatherStationData> subset = allData.subList(allData.size() - dataSize, allData.size());

        List<SvgSparkLine.DataPoint> tempPoints = subset.stream()
                .filter(RawWeatherStationData::hasValidAirTemp)
                .map(d -> SvgSparkLine.DataPoint.of(d.getInstant(), d.getTempfC()))
                .toList();

        List<SvgSparkLine.DataPoint> windPoints = subset.stream()
                .map(d -> SvgSparkLine.DataPoint.of(d.getInstant(), d.getWindSpeedMs()))
                .toList();

        System.out.println("Series Count | Moving Avg     | RDP");
        System.out.println("-------------|----------------|----------------");

        for (int seriesCount : new int[]{1, 2, 3, 5}) {
            long maTime = measureMultiSeriesDrawTime(tempPoints, windPoints, seriesCount, SvgSparkLine.Smoothing.MOVING_AVERAGE);
            long rdpTime = measureMultiSeriesDrawTime(tempPoints, windPoints, seriesCount, SvgSparkLine.Smoothing.RDP);

            // Record multi-series measurements
            baseline.record("multiseries.movingAvg." + seriesCount + "series", maTime);
            baseline.record("multiseries.rdp." + seriesCount + "series", rdpTime);

            System.out.printf("%12d | %14s | %s%n",
                    seriesCount, formatTime(maTime), formatTime(rdpTime));
        }

        System.out.println("\nDraw time scales with number of series.");
    }

    /**
     * Validates that key performance metrics are within acceptable tolerance.
     * This test runs last and fails if any metric regressed significantly.
     */
    @Test
    @Order(100)
    void validatePerformanceNotRegressed() {
        System.out.println("\n=== Performance Regression Validation ===\n");
        System.out.println("Checking key metrics against baseline (tolerance: " + (int)(TOLERANCE * 100) + "%)...\n");

        // Key metrics to validate - these are the most important for real-world usage
        String[] criticalMetrics = {
                // Large dataset with smoothing (most common use case)
                "sparkline.movingAvg.100000",
                "sparkline.rdp.100000",
                // Full dataset
                "sparkline.movingAvg." + allData.size(),
                "sparkline.rdp." + allData.size(),
                // WindRose operations
                "windrose.draw.100000",
                // Multi-series
                "multiseries.movingAvg.3series",
                "multiseries.rdp.3series"
        };

        int failures = 0;
        for (String metric : criticalMetrics) {
            try {
                baseline.assertWithinTolerance(metric, TOLERANCE);
            } catch (AssertionError e) {
                failures++;
                System.err.println("FAIL: " + e.getMessage());
            }
        }

        if (failures > 0) {
            throw new AssertionError(failures + " performance metric(s) exceeded tolerance. " +
                    "Review output above and update baseline if regression is expected.");
        }

        System.out.println("\nAll critical metrics within tolerance.");
    }

    private long measureDrawTime(List<SvgSparkLine.DataPoint> points, SvgSparkLine.Smoothing smoothing) {
        UI ui = UI.getCurrent();

        // Warmup - measure add() which triggers onAttach() -> draw()
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            SvgSparkLine sparkLine = new SvgSparkLine(1000, 100);
            sparkLine.setSmoothing(smoothing);
            sparkLine.setData(points);
            ui.add(sparkLine);  // This triggers draw() via onAttach
            ui.remove(sparkLine);
        }

        // Measure - add() triggers onAttach() which calls draw()
        long totalTime = 0;
        for (int i = 0; i < MEASURE_ITERATIONS; i++) {
            SvgSparkLine sparkLine = new SvgSparkLine(1000, 100);
            sparkLine.setSmoothing(smoothing);
            sparkLine.setData(points);

            long start = System.nanoTime();
            ui.add(sparkLine);  // This triggers draw() via onAttach
            totalTime += System.nanoTime() - start;

            ui.remove(sparkLine);
        }

        return totalTime / MEASURE_ITERATIONS;
    }

    private int countElementsAfterDraw(List<SvgSparkLine.DataPoint> points, SvgSparkLine.Smoothing smoothing) {
        UI ui = UI.getCurrent();
        SvgSparkLine sparkLine = new SvgSparkLine(1000, 100);
        sparkLine.setSmoothing(smoothing);
        sparkLine.setData(points);
        ui.add(sparkLine);  // This triggers draw() via onAttach
        // Count SVG child elements (lines, paths, text labels)
        int count = sparkLine.getElement().getChildCount();
        ui.remove(sparkLine);
        return count;
    }

    private long measureWindRoseDrawTime(double[] duration, double[] energy, int sectors) {
        UI ui = UI.getCurrent();

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            WindRose windRose = new WindRose(300, sectors);
            windRose.addSeries("Duration", NamedColor.BLUE, duration);
            windRose.addSeries("Energy", NamedColor.RED, energy);
            windRose.draw();  // WindRose doesn't auto-draw on attach
            ui.add(windRose);
            ui.remove(windRose);
        }

        // Measure
        long totalTime = 0;
        for (int i = 0; i < MEASURE_ITERATIONS; i++) {
            WindRose windRose = new WindRose(300, sectors);
            windRose.addSeries("Duration", NamedColor.BLUE, duration);
            windRose.addSeries("Energy", NamedColor.RED, energy);

            long start = System.nanoTime();
            windRose.draw();  // WindRose doesn't auto-draw on attach
            totalTime += System.nanoTime() - start;

            ui.add(windRose);
            ui.remove(windRose);
        }

        return totalTime / MEASURE_ITERATIONS;
    }

    private long measureMultiSeriesDrawTime(List<SvgSparkLine.DataPoint> primary,
                                            List<SvgSparkLine.DataPoint> secondary,
                                            int seriesCount,
                                            SvgSparkLine.Smoothing smoothing) {
        UI ui = UI.getCurrent();

        // Warmup - add() triggers onAttach() -> draw()
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            SvgSparkLine sparkLine = createMultiSeriesSparkLine(primary, secondary, seriesCount, smoothing);
            ui.add(sparkLine);
            ui.remove(sparkLine);
        }

        // Measure - add() triggers onAttach() which calls draw()
        long totalTime = 0;
        for (int i = 0; i < MEASURE_ITERATIONS; i++) {
            SvgSparkLine sparkLine = createMultiSeriesSparkLine(primary, secondary, seriesCount, smoothing);

            long start = System.nanoTime();
            ui.add(sparkLine);
            totalTime += System.nanoTime() - start;

            ui.remove(sparkLine);
        }

        return totalTime / MEASURE_ITERATIONS;
    }

    private SvgSparkLine createMultiSeriesSparkLine(List<SvgSparkLine.DataPoint> primary,
                                                     List<SvgSparkLine.DataPoint> secondary,
                                                     int seriesCount,
                                                     SvgSparkLine.Smoothing smoothing) {
        SvgSparkLine sparkLine = new SvgSparkLine(1000, 100);
        sparkLine.setSmoothing(smoothing);
        sparkLine.setData(primary);
        sparkLine.setLineColor(NamedColor.BLUE);

        NamedColor[] colors = {NamedColor.RED, NamedColor.GREEN, NamedColor.ORANGE, NamedColor.PURPLE};
        for (int s = 1; s < seriesCount; s++) {
            sparkLine.addSeries(secondary, colors[(s - 1) % colors.length]);
        }

        return sparkLine;
    }

    private String formatTime(long nanos) {
        if (nanos < 1000) {
            return nanos + " ns";
        } else if (nanos < 1_000_000) {
            return String.format("%.1f µs", nanos / 1000.0);
        } else if (nanos < 1_000_000_000) {
            return String.format("%.2f ms", nanos / 1_000_000.0);
        } else {
            return String.format("%.2f s", nanos / 1_000_000_000.0);
        }
    }
}
