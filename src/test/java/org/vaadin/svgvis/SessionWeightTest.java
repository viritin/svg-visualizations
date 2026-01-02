package org.vaadin.svgvis;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.vaadin.svgvis.testdata.RawWeatherStationData;
import org.vaadin.svgvis.testdata.WeatherData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Test that measures and reports the session weight (serialized size) of
 * data structures with different dataset sizes.
 *
 * This helps understand how much memory visualizations consume in session
 * when storing different amounts of data.
 */
public class SessionWeightTest {

    private static List<RawWeatherStationData> allData;

    @BeforeAll
    static void loadData() {
        allData = WeatherData.getAll();
        System.out.println("Loaded " + allData.size() + " weather records for testing");
    }

    @Test
    void measureDataPointsSessionWeight() throws IOException {
        System.out.println("\n=== DataPoints Session Weight Analysis ===\n");
        System.out.println("Measuring serialized size of SvgSparkLine.DataPoint lists...\n");

        int[] dataSizes = {100, 1000, 10000, 50000, 100000, 500000, allData.size()};

        for (int size : dataSizes) {
            if (size > allData.size()) continue;

            List<RawWeatherStationData> subset = allData.subList(allData.size() - size, allData.size());

            List<SvgSparkLine.DataPoint> points = subset.stream()
                    .filter(RawWeatherStationData::hasValidAirTemp)
                    .map(d -> SvgSparkLine.DataPoint.of(d.getInstant(), d.getTempfC()))
                    .toList();

            // Serialize the points as ArrayList (like component would store)
            int serializedSize = measureSerializedSize(new ArrayList<>(points));
            double bytesPerPoint = points.size() > 0 ? (double) serializedSize / points.size() : 0;

            System.out.printf("Data points: %,9d -> Serialized: %10s (%.1f bytes/point)%n",
                    points.size(), formatBytes(serializedSize), bytesPerPoint);
        }

        System.out.println();
    }

    @Test
    void measureWindRoseDataSessionWeight() throws IOException {
        System.out.println("\n=== WindRose Data Session Weight Analysis ===\n");
        System.out.println("WindRose aggregates data into fixed sectors, so weight is constant.\n");

        int[] dataSizes = {100, 1000, 10000, 50000, 100000, 500000, allData.size()};

        for (int size : dataSizes) {
            if (size > allData.size()) continue;

            List<RawWeatherStationData> subset = allData.subList(allData.size() - size, allData.size());

            int sectors = 16;
            double[] duration = new double[sectors];
            double[] energy = new double[sectors];
            double degreesPerSector = 360.0 / sectors;

            for (RawWeatherStationData d : subset) {
                int dir = d.getWinddir();
                int sectorIndex = (int) Math.round(dir / degreesPerSector) % sectors;
                duration[sectorIndex]++;
                double speed = d.getWindSpeedMs();
                energy[sectorIndex] += speed * speed * speed;
            }

            // WindRose stores arrays, not raw data
            WindRose.DataSeries durationSeries = new WindRose.DataSeries("Duration", null, duration);
            WindRose.DataSeries energySeries = new WindRose.DataSeries("Energy", null, energy);
            List<WindRose.DataSeries> seriesList = List.of(durationSeries, energySeries);

            int serializedSize = measureSerializedSize(new ArrayList<>(seriesList));

            System.out.printf("Input records: %,9d -> Aggregated to %d sectors -> Serialized: %s%n",
                    size, sectors, formatBytes(serializedSize));
        }

        System.out.println("\nConclusion: WindRose weight is ~constant regardless of input size!");
    }

    @Test
    void measureRawDataSessionWeight() throws IOException {
        System.out.println("\n=== Raw Weather Data Session Weight ===\n");
        System.out.println("For comparison: size of raw RawWeatherStationData objects...\n");

        int[] dataSizes = {100, 1000, 10000, 50000, 100000, 500000, allData.size()};

        for (int size : dataSizes) {
            if (size > allData.size()) continue;

            List<RawWeatherStationData> subset = new ArrayList<>(
                    allData.subList(allData.size() - size, allData.size()));

            int serializedSize = measureSerializedSize(subset);
            double bytesPerRecord = size > 0 ? (double) serializedSize / size : 0;

            System.out.printf("Raw records:  %,9d -> Serialized: %10s (%.0f bytes/record)%n",
                    size, formatBytes(serializedSize), bytesPerRecord);
        }

        System.out.println();
    }

    @Test
    void sessionWeightGrowthComparison() throws IOException {
        System.out.println("\n=== Session Weight Growth Comparison ===\n");
        System.out.println("Comparing growth patterns of different data storage approaches...\n");

        int[] dataSizes = {1000, 10000, 100000, allData.size()};

        System.out.println("Size         | Raw Data       | Before Draw    | After Smooth   | After Draw     | WindRose");
        System.out.println("-------------|----------------|----------------|----------------|----------------|----------");

        for (int size : dataSizes) {
            if (size > allData.size()) continue;

            List<RawWeatherStationData> subset = allData.subList(allData.size() - size, allData.size());

            // Raw data
            int rawSize = measureSerializedSize(new ArrayList<>(subset));

            // DataPoints before smoothing (what was stored before the fix)
            List<SvgSparkLine.DataPoint> pointsBefore = subset.stream()
                    .map(d -> SvgSparkLine.DataPoint.of(d.getInstant(), d.getTempfC()))
                    .toList();
            int beforeSize = measureSerializedSize(new ArrayList<>(pointsBefore));

            // DataPoints after MOVING_AVERAGE smoothing (before clearing)
            List<SvgSparkLine.DataPoint> pointsMovingAvg = applyMovingAverage(pointsBefore);
            int afterSmoothSize = measureSerializedSize(new ArrayList<>(pointsMovingAvg));

            // After draw() clears data - empty list
            int afterDrawSize = measureSerializedSize(new ArrayList<>(List.of()));

            // WindRose aggregated data
            int sectors = 16;
            double[] duration = new double[sectors];
            for (RawWeatherStationData d : subset) {
                int sectorIndex = (int) Math.round(d.getWinddir() / 22.5) % sectors;
                duration[sectorIndex]++;
            }
            int windRoseSize = measureSerializedSize(duration);

            System.out.printf("%,12d | %14s | %14s | %14s | %14s | %s%n",
                    size, formatBytes(rawSize), formatBytes(beforeSize),
                    formatBytes(afterSmoothSize), formatBytes(afterDrawSize), formatBytes(windRoseSize));
        }

        System.out.println("\nKey insight: After draw(), SparkLine clears all data - session weight is ~0!");
        System.out.println("Data is only needed during draw(), SVG elements retain the visualization.");
    }

    @Test
    void measureRdpReduction() throws IOException {
        System.out.println("\n=== RDP Algorithm Point Reduction ===\n");
        System.out.println("Measuring how RDP reduces data points while preserving shape...\n");

        int[] dataSizes = {100, 1000, 10000, 50000, 100000, allData.size()};

        System.out.println("Input Points | RDP Points | Reduction | Serialized Size");
        System.out.println("-------------|------------|-----------|----------------");

        for (int size : dataSizes) {
            if (size > allData.size()) continue;

            List<RawWeatherStationData> subset = allData.subList(allData.size() - size, allData.size());

            List<SvgSparkLine.DataPoint> points = subset.stream()
                    .filter(RawWeatherStationData::hasValidAirTemp)
                    .map(d -> SvgSparkLine.DataPoint.of(d.getInstant(), d.getTempfC()))
                    .toList();

            List<SvgSparkLine.DataPoint> rdpPoints = applyRdp(points);
            int serializedSize = measureSerializedSize(new ArrayList<>(rdpPoints));
            double reduction = points.size() > 0 ? (1.0 - (double) rdpPoints.size() / points.size()) * 100 : 0;

            System.out.printf("%,12d | %10d | %8.1f%% | %s%n",
                    points.size(), rdpPoints.size(), reduction, formatBytes(serializedSize));
        }

        System.out.println("\nRDP preserves shape-significant points, so reduction varies with data complexity.");
    }

    /**
     * Simulates the moving average smoothing from SvgSparkLine.
     * TARGET_POINTS is 50. First normalizes x values to 0-1 range like the component does.
     */
    private List<SvgSparkLine.DataPoint> applyMovingAverage(List<SvgSparkLine.DataPoint> data) {
        int TARGET_POINTS = 50;
        if (data.size() <= TARGET_POINTS) {
            return data;
        }

        // Normalize x values to 0-1 range (like SvgSparkLine.normalizeDataPoints does)
        double minX = data.stream().mapToDouble(SvgSparkLine.DataPoint::x).min().orElse(0);
        double maxX = data.stream().mapToDouble(SvgSparkLine.DataPoint::x).max().orElse(1);
        double rangeX = maxX - minX;
        if (rangeX < 0.001) rangeX = 1;

        List<SvgSparkLine.DataPoint> normalized = new ArrayList<>(data.size());
        for (SvgSparkLine.DataPoint dp : data) {
            double normX = (dp.x() - minX) / rangeX;
            normalized.add(new SvgSparkLine.DataPoint(normX, dp.y()));
        }

        // Now apply moving average on normalized data
        double dataMinX = 0;
        double dataMaxX = 1;
        double dataRange = 1.0;

        int numBuckets = Math.max(3, (int) (TARGET_POINTS * dataRange));
        double bucketWidth = dataRange / numBuckets;

        List<SvgSparkLine.DataPoint> result = new ArrayList<>(numBuckets);

        for (int i = 0; i < numBuckets; i++) {
            double bucketStart = dataMinX + i * bucketWidth;
            double bucketEnd = dataMinX + (i + 1) * bucketWidth;
            double bucketCenter = (bucketStart + bucketEnd) / 2;

            double sum = 0;
            int count = 0;
            for (SvgSparkLine.DataPoint dp : normalized) {
                if (dp.x() >= bucketStart && dp.x() < bucketEnd) {
                    sum += dp.y();
                    count++;
                }
            }

            if (count > 0) {
                result.add(new SvgSparkLine.DataPoint(bucketCenter, sum / count));
            }
        }

        return result;
    }

    /**
     * Simulates the RDP (Ramer-Douglas-Peucker) algorithm from SvgSparkLine.
     * First normalizes x values to 0-1 range, then applies RDP.
     */
    private List<SvgSparkLine.DataPoint> applyRdp(List<SvgSparkLine.DataPoint> data) {
        if (data.size() < 3) {
            return data;
        }

        // Normalize x values to 0-1 range (like SvgSparkLine.normalizeDataPoints does)
        double minX = data.stream().mapToDouble(SvgSparkLine.DataPoint::x).min().orElse(0);
        double maxX = data.stream().mapToDouble(SvgSparkLine.DataPoint::x).max().orElse(1);
        double rangeX = maxX - minX;
        if (rangeX < 0.001) rangeX = 1;

        // Also normalize y values for epsilon calculation
        double minY = data.stream().mapToDouble(SvgSparkLine.DataPoint::y).min().orElse(0);
        double maxY = data.stream().mapToDouble(SvgSparkLine.DataPoint::y).max().orElse(1);
        double rangeY = maxY - minY;
        if (rangeY < 0.001) rangeY = 1;

        List<double[]> normalized = new ArrayList<>(data.size());
        for (SvgSparkLine.DataPoint dp : data) {
            double normX = (dp.x() - minX) / rangeX;
            double normY = (dp.y() - minY) / rangeY;
            normalized.add(new double[]{normX, normY});
        }

        // Apply RDP with typical epsilon (viewBoxWidth=1000, rdpEpsilon=10)
        double epsilon = 10.0 / 1000.0;  // normalizedEpsilon
        List<double[]> reduced = ramerDouglasPeucker(normalized, epsilon);

        // Convert back to DataPoints with original y values
        List<SvgSparkLine.DataPoint> result = new ArrayList<>(reduced.size());
        for (double[] pt : reduced) {
            double originalY = pt[1] * rangeY + minY;
            result.add(new SvgSparkLine.DataPoint(pt[0], originalY));
        }

        return result;
    }

    private List<double[]> ramerDouglasPeucker(List<double[]> points, double epsilon) {
        if (points.size() < 3) {
            return new ArrayList<>(points);
        }

        double maxDist = 0;
        int maxIndex = 0;
        double[] first = points.get(0);
        double[] last = points.get(points.size() - 1);

        for (int i = 1; i < points.size() - 1; i++) {
            double dist = perpendicularDistance(points.get(i), first, last);
            if (dist > maxDist) {
                maxDist = dist;
                maxIndex = i;
            }
        }

        if (maxDist > epsilon) {
            List<double[]> left = ramerDouglasPeucker(points.subList(0, maxIndex + 1), epsilon);
            List<double[]> right = ramerDouglasPeucker(points.subList(maxIndex, points.size()), epsilon);

            List<double[]> result = new ArrayList<>(left.subList(0, left.size() - 1));
            result.addAll(right);
            return result;
        } else {
            List<double[]> result = new ArrayList<>(2);
            result.add(first);
            result.add(last);
            return result;
        }
    }

    private double perpendicularDistance(double[] point, double[] lineStart, double[] lineEnd) {
        double dx = lineEnd[0] - lineStart[0];
        double dy = lineEnd[1] - lineStart[1];

        double lineLengthSquared = dx * dx + dy * dy;
        if (lineLengthSquared == 0) {
            dx = point[0] - lineStart[0];
            dy = point[1] - lineStart[1];
            return Math.sqrt(dx * dx + dy * dy);
        }

        double area2 = Math.abs(
                (lineEnd[0] - lineStart[0]) * (lineStart[1] - point[1]) -
                (lineStart[0] - point[0]) * (lineEnd[1] - lineStart[1])
        );

        return area2 / Math.sqrt(lineLengthSquared);
    }

    private int measureSerializedSize(Object obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(obj);
        oos.close();
        return baos.size();
    }

    private String formatBytes(int bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
}
