package org.vaadin.svgvis;

import org.vaadin.svgvis.SvgSparkLine.DataPoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The arithmetic behind {@link SvgSparkLine}'s curve, kept apart from the SVG
 * so that it can be tested as numbers: downsampling, gap detection and the
 * curve through the points.
 */
final class SparkLineGeometry {

    /** A gap is an interval this many times longer than the typical one around it. */
    static final double GAP_FACTOR = 5;
    /** How many intervals on each side make up "around it". */
    private static final int GAP_WINDOW = 3;

    private SparkLineGeometry() {
    }

    /**
     * Largest-Triangle-Three-Buckets (Steinarsson 2013): picks {@code threshold}
     * of the actual points, the one in each bucket that spans the largest
     * triangle with its neighbours, so peaks and turns survive. The first and
     * last points are always kept, so the curve ends at the latest reading
     * instead of an average that lags behind it.
     *
     * @param data      points in x order
     * @param threshold how many points to keep, three or more
     * @return the kept points, or the data as is when it has no more than that
     */
    static List<DataPoint> lttb(List<DataPoint> data, int threshold) {
        int n = data.size();
        if (threshold >= n || threshold < 3) {
            return data;
        }
        List<DataPoint> sampled = new ArrayList<>(threshold);
        double every = (double) (n - 2) / (threshold - 2);
        int a = 0;
        sampled.add(data.getFirst());
        for (int i = 0; i < threshold - 2; i++) {
            // The average of the next bucket is the triangle's third corner
            int nextStart = (int) Math.floor((i + 1) * every) + 1;
            int nextEnd = Math.min((int) Math.floor((i + 2) * every) + 1, n);
            double avgX = 0;
            double avgY = 0;
            for (int j = nextStart; j < nextEnd; j++) {
                avgX += data.get(j).x();
                avgY += data.get(j).y();
            }
            int count = Math.max(1, nextEnd - nextStart);
            avgX /= count;
            avgY /= count;

            int start = (int) Math.floor(i * every) + 1;
            int end = (int) Math.floor((i + 1) * every) + 1;
            DataPoint pa = data.get(a);
            double maxArea = -1;
            int chosen = start;
            for (int j = start; j < end; j++) {
                DataPoint p = data.get(j);
                double area = Math.abs((pa.x() - avgX) * (p.y() - pa.y())
                        - (pa.x() - p.x()) * (avgY - pa.y()));
                if (area > maxArea) {
                    maxArea = area;
                    chosen = j;
                }
            }
            sampled.add(data.get(chosen));
            a = chosen;
        }
        sampled.add(data.getLast());
        return sampled;
    }

    /**
     * Splits screen points into runs at gaps, where an interval is more than
     * {@link #GAP_FACTOR} times the median of the intervals around it: a smooth
     * curve across a gap would claim readings that were never taken. The median
     * is local because sensors change pace (a burst every few seconds, then a
     * reading every few minutes), and against one global median every interval
     * of the slower part would count as a gap. Points sharing an x with the
     * previous one are dropped, as a curve cannot go through both.
     *
     * @param points {x, y} pairs in x order
     * @return the runs, each at least one point
     */
    static List<List<double[]>> splitAtGaps(List<double[]> points) {
        List<double[]> distinct = new ArrayList<>(points.size());
        for (double[] p : points) {
            if (distinct.isEmpty() || p[0] > distinct.getLast()[0]) {
                distinct.add(p);
            }
        }
        List<List<double[]>> runs = new ArrayList<>();
        if (distinct.isEmpty()) {
            return runs;
        }
        double[] intervals = new double[distinct.size() - 1];
        for (int i = 1; i < distinct.size(); i++) {
            intervals[i - 1] = distinct.get(i)[0] - distinct.get(i - 1)[0];
        }
        List<double[]> run = new ArrayList<>();
        run.add(distinct.getFirst());
        for (int i = 0; i < intervals.length; i++) {
            if (isGap(intervals, i)) {
                runs.add(run);
                run = new ArrayList<>();
            }
            run.add(distinct.get(i + 1));
        }
        runs.add(run);
        return runs;
    }

    private static boolean isGap(double[] intervals, int index) {
        int from = Math.max(0, index - GAP_WINDOW);
        int to = Math.min(intervals.length, index + GAP_WINDOW + 1);
        double[] around = new double[to - from - 1];
        int k = 0;
        for (int j = from; j < to; j++) {
            if (j != index) {
                around[k++] = intervals[j];
            }
        }
        if (around.length == 0) {
            return false; // two points: nothing to compare with
        }
        Arrays.sort(around);
        int middle = around.length / 2;
        double median = around.length % 2 == 1 ? around[middle] : (around[middle - 1] + around[middle]) / 2;
        return intervals[index] > GAP_FACTOR * median;
    }

    /**
     * Monotone cubic interpolation (Fritsch–Carlson, as in d3's curveMonotoneX):
     * the cubic Bézier segments of a curve through the points. It respects
     * uneven spacing, so no control point leaves its segment and the curve never
     * turns back in x, and it never over- or undershoots between two points.
     *
     * @param points {x, y} pairs with strictly increasing x, two or more
     * @return per segment {cp1x, cp1y, cp2x, cp2y, x, y}, ending at the next point
     */
    static List<double[]> monotoneCurve(List<double[]> points) {
        int n = points.size();
        List<double[]> segments = new ArrayList<>(Math.max(0, n - 1));
        if (n < 2) {
            return segments;
        }
        double[] h = new double[n - 1];
        double[] s = new double[n - 1];
        for (int i = 0; i < n - 1; i++) {
            h[i] = points.get(i + 1)[0] - points.get(i)[0];
            s[i] = (points.get(i + 1)[1] - points.get(i)[1]) / h[i];
        }
        double[] m = new double[n];
        for (int i = 1; i < n - 1; i++) {
            // Flat at a local extreme; elsewhere a weighted slope, limited so the curve cannot overshoot
            if (s[i - 1] * s[i] <= 0) {
                m[i] = 0;
            } else {
                double p = (s[i - 1] * h[i] + s[i] * h[i - 1]) / (h[i - 1] + h[i]);
                m[i] = Math.signum(s[i]) * Math.min(Math.min(Math.abs(s[i - 1]), Math.abs(s[i])), 0.5 * Math.abs(p)) * 2;
            }
        }
        if (n == 2) {
            m[0] = s[0];
            m[1] = s[0];
        } else {
            m[0] = endTangent(s[0], m[1]);
            m[n - 1] = endTangent(s[n - 2], m[n - 2]);
        }
        for (int i = 0; i < n - 1; i++) {
            double[] p0 = points.get(i);
            double[] p1 = points.get(i + 1);
            double third = h[i] / 3;
            segments.add(new double[]{
                    p0[0] + third, p0[1] + m[i] * third,
                    p1[0] - third, p1[1] - m[i + 1] * third,
                    p1[0], p1[1]});
        }
        return segments;
    }

    /** The end slope that makes the first or last segment a parabola through its neighbour's tangent. */
    private static double endTangent(double slope, double neighbour) {
        double t = (3 * slope - neighbour) / 2;
        // Keep the end from swinging past the segment's own direction
        if (Math.signum(t) != Math.signum(slope)) {
            return 0;
        }
        if (Math.abs(t) > 3 * Math.abs(slope)) {
            return 3 * slope;
        }
        return t;
    }
}
