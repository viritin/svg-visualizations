package org.vaadin.svgvis;

import org.junit.jupiter.api.Test;
import org.vaadin.svgvis.SvgSparkLine.DataPoint;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparkLineGeometryTest {

    /** A reading every second, a rise at the very end, and one spike in the middle. */
    private static List<DataPoint> readings() {
        List<DataPoint> data = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            double y = 20 + Math.sin(i / 50.0) * 0.2;
            if (i == 500) y = 30;
            if (i > 990) y = 20 + (i - 990) * 0.5;
            data.add(new DataPoint(i, y));
        }
        return data;
    }

    @Test
    void lttbKeepsTheLatestReadingAndThePeaks() {
        List<DataPoint> data = readings();
        List<DataPoint> sampled = SparkLineGeometry.lttb(data, 100);
        assertEquals(100, sampled.size());
        assertEquals(data.getFirst(), sampled.getFirst());
        assertEquals(data.getLast(), sampled.getLast(), "the curve ends at the latest reading, not an average");
        assertTrue(sampled.stream().anyMatch(p -> p.y() == 30), "the spike is an actual point that survives");
        assertTrue(data.containsAll(sampled), "only actual readings, no averages");
    }

    @Test
    void lttbLeavesShortDataAlone() {
        List<DataPoint> data = readings().subList(0, 50);
        assertEquals(data, SparkLineGeometry.lttb(data, 100));
    }

    /** Dense readings, a long gap and dense readings again: the case that looped. */
    private static List<double[]> gappy() {
        List<double[]> points = new ArrayList<>();
        for (int i = 0; i < 10; i++) points.add(new double[]{i * 2, 50 + (i % 2) * 5});
        for (int i = 0; i < 10; i++) points.add(new double[]{900 + i * 2, 20 + (i % 3) * 4});
        return points;
    }

    @Test
    void aLongIntervalSplitsTheRun() {
        List<List<double[]>> runs = SparkLineGeometry.splitAtGaps(gappy());
        assertEquals(2, runs.size());
        assertEquals(10, runs.get(0).size());
        assertEquals(10, runs.get(1).size());
    }

    /** A fast burst, a gap, then readings at a slower pace: the slower pace is not a gap. */
    @Test
    void aChangeOfPaceIsNotAGap() {
        List<double[]> points = new ArrayList<>();
        for (int i = 0; i < 40; i++) points.add(new double[]{i * 0.2, 50});
        for (int i = 0; i < 6; i++) points.add(new double[]{990 + i * 2.4, 40 + i});
        List<List<double[]>> runs = SparkLineGeometry.splitAtGaps(points);
        assertEquals(2, runs.size(), "only the long silence splits");
        assertEquals(6, runs.get(1).size());
    }

    @Test
    void oneMissedReadingIsNotAGap() {
        List<double[]> points = new ArrayList<>();
        for (int i = 0; i < 30; i++) if (i != 15) points.add(new double[]{i * 10, i % 4});
        assertEquals(1, SparkLineGeometry.splitAtGaps(points).size());
    }

    @Test
    void evenReadingsAreOneRun() {
        List<double[]> points = new ArrayList<>();
        for (int i = 0; i < 50; i++) points.add(new double[]{i * 10, i % 7});
        assertEquals(1, SparkLineGeometry.splitAtGaps(points).size());
    }

    @Test
    void theCurveStaysWithinEachSegment() {
        // Uneven spacing inside one run, as the old curve's control points overshot on
        List<double[]> points = List.of(
                new double[]{0, 50}, new double[]{1, 52}, new double[]{2, 49},
                new double[]{300, 30}, new double[]{301, 35}, new double[]{302, 31});
        List<double[]> curve = SparkLineGeometry.monotoneCurve(points);
        assertEquals(points.size() - 1, curve.size());
        for (int i = 0; i < curve.size(); i++) {
            double[] from = points.get(i);
            double[] to = points.get(i + 1);
            double[] c = curve.get(i);
            for (double x : new double[]{c[0], c[2]}) {
                assertTrue(x >= from[0] && x <= to[0], "control point x " + x + " outside segment " + i);
            }
            double low = Math.min(from[1], to[1]);
            double high = Math.max(from[1], to[1]);
            for (double y : new double[]{c[1], c[3]}) {
                assertTrue(y >= low - 1e-9 && y <= high + 1e-9, "control point y " + y + " overshoots segment " + i);
            }
            assertEquals(to[0], c[4]);
            assertEquals(to[1], c[5]);
        }
    }
}
