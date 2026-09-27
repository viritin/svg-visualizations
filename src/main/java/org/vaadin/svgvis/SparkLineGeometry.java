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
     * Averages the data in about {@code target} buckets of equal x width, each
     * drawn at its centre: sensor noise becomes a calm curve. The buckets'
     * centres would leave the ends half a bucket short, and the last bucket mixes
     * the latest readings with older ones, so a rise took half a bucket to show.
     * The curve therefore also has a point at the first and at the last reading,
     * each the average of the readings within half a bucket of it: it reaches the
     * latest reading and turns with it, still without the noise.
     *
     * @param data   points; x may cover only part of 0–1 with a fixed x range
     * @param target about how many buckets the whole 0–1 range is divided into
     * @return the averages, or the data as is when it is short or too narrow
     */
    static List<DataPoint> movingAverage(List<DataPoint> data, int target) {
        if (data.size() <= target) {
            return data;
        }
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        for (DataPoint dp : data) {
            minX = Math.min(minX, dp.x());
            maxX = Math.max(maxX, dp.x());
        }
        double range = maxX - minX;
        if (range < 0.01) {
            return data;
        }
        // As many buckets as this part of the 0–1 range deserves
        int buckets = Math.max(3, (int) (target * range));
        double width = range / buckets;
        double[] sums = new double[buckets];
        int[] counts = new int[buckets];
        double firstSum = 0;
        int firstCount = 0;
        double lastSum = 0;
        int lastCount = 0;
        // One pass; the last reading belongs to the last bucket, not past it
        for (DataPoint dp : data) {
            int bucket = Math.min(buckets - 1, (int) ((dp.x() - minX) / width));
            sums[bucket] += dp.y();
            counts[bucket]++;
            if (dp.x() <= minX + width / 2) {
                firstSum += dp.y();
                firstCount++;
            }
            if (dp.x() >= maxX - width / 2) {
                lastSum += dp.y();
                lastCount++;
            }
        }
        List<DataPoint> result = new ArrayList<>(buckets + 2);
        result.add(new DataPoint(minX, firstSum / firstCount));
        for (int i = 0; i < buckets; i++) {
            if (counts[i] > 0) {
                result.add(new DataPoint(minX + (i + 0.5) * width, sums[i] / counts[i]));
            }
        }
        result.add(new DataPoint(maxX, lastSum / lastCount));
        // Very sparse data leaves too few buckets filled to draw anything better
        return result.size() < 5 ? data : result;
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
     * The gaps in the data, as {from, to} x ranges: intervals more than
     * {@link #GAP_FACTOR} times the median of the intervals around them. A smooth
     * curve across a gap would claim readings that were never taken. The median
     * is local because sensors change pace (a burst every few seconds, then a
     * reading every few minutes), and against one global median every interval
     * of the slower part would count as a gap.
     * <p>
     * Found in the data before it is downsampled: the points a downsampler keeps
     * are unevenly spaced by nature, and their spacing would show gaps that are
     * not there.
     *
     * @param points {x, y} pairs in x order
     */
    static List<double[]> gaps(List<double[]> points) {
        double[] xs = new double[points.size()];
        for (int i = 0; i < xs.length; i++) {
            xs[i] = points.get(i)[0];
        }
        return gaps(xs);
    }

    /**
     * The gaps of x values in ascending order; see {@link #gaps(List)}. Runs on
     * every drawing over all the raw data, so it keeps to primitive arrays and one
     * reused buffer: a million readings allocate two arrays, not a million.
     */
    static List<double[]> gaps(double[] xs) {
        // Intervals and the x they start from, skipping repeated x values, which
        // would make zero-length intervals
        double[] intervals = new double[Math.max(0, xs.length - 1)];
        double[] starts = new double[intervals.length];
        int n = 0;
        double previous = xs.length > 0 ? xs[0] : 0;
        for (int i = 1; i < xs.length; i++) {
            if (xs[i] > previous) {
                starts[n] = previous;
                intervals[n++] = xs[i] - previous;
                previous = xs[i];
            }
        }
        List<double[]> gaps = new ArrayList<>();
        if (n < 2) {
            return gaps;
        }
        double[] around = new double[2 * GAP_WINDOW];
        for (int i = 0; i < n; i++) {
            // The median is at least the shortest interval around: most intervals
            // are ruled out by that alone, without sorting anything
            if (intervals[i] > GAP_FACTOR * localMin(intervals, n, i)
                    && intervals[i] > GAP_FACTOR * localMedian(intervals, n, i, around)) {
                gaps.add(new double[]{starts[i], starts[i] + intervals[i]});
            }
        }
        return gaps;
    }

    private static double localMin(double[] intervals, int n, int index) {
        double min = Double.POSITIVE_INFINITY;
        for (int j = Math.max(0, index - GAP_WINDOW); j < Math.min(n, index + GAP_WINDOW + 1); j++) {
            if (j != index && intervals[j] < min) {
                min = intervals[j];
            }
        }
        return min;
    }

    /** Splits points into runs at their own gaps; see {@link #gaps(List)}. */
    static List<List<double[]>> splitAtGaps(List<double[]> points) {
        return splitAtGaps(points, gaps(points));
    }

    /**
     * Splits points into runs wherever one of the given gaps lies between two
     * consecutive points. Points sharing an x with the previous one are dropped,
     * as a curve cannot go through both.
     *
     * @param points {x, y} pairs in x order
     * @param gaps   {from, to} x ranges, in the points' x units
     * @return the runs, each at least one point
     */
    static List<List<double[]>> splitAtGaps(List<double[]> points, List<double[]> gaps) {
        List<double[]> distinct = distinctX(points);
        List<List<double[]>> runs = new ArrayList<>();
        if (distinct.isEmpty()) {
            return runs;
        }
        List<double[]> run = new ArrayList<>();
        run.add(distinct.getFirst());
        for (int i = 1; i < distinct.size(); i++) {
            double from = distinct.get(i - 1)[0];
            double to = distinct.get(i)[0];
            for (double[] gap : gaps) {
                if (from <= gap[0] + 1e-9 && to >= gap[1] - 1e-9) {
                    runs.add(run);
                    run = new ArrayList<>();
                    break;
                }
            }
            run.add(distinct.get(i));
        }
        runs.add(run);
        return runs;
    }

    private static List<double[]> distinctX(List<double[]> points) {
        List<double[]> distinct = new ArrayList<>(points.size());
        for (double[] p : points) {
            if (distinct.isEmpty() || p[0] > distinct.getLast()[0]) {
                distinct.add(p);
            }
        }
        return distinct;
    }

    /** The median of the intervals around {@code index}, sorted in the given buffer by insertion. */
    private static double localMedian(double[] intervals, int n, int index, double[] buffer) {
        int from = Math.max(0, index - GAP_WINDOW);
        int to = Math.min(n, index + GAP_WINDOW + 1);
        int count = 0;
        for (int j = from; j < to; j++) {
            if (j == index) {
                continue;
            }
            double value = intervals[j];
            int k = count++;
            while (k > 0 && buffer[k - 1] > value) {
                buffer[k] = buffer[k - 1];
                k--;
            }
            buffer[k] = value;
        }
        int middle = count / 2;
        return count % 2 == 1 ? buffer[middle] : (buffer[middle - 1] + buffer[middle]) / 2;
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
