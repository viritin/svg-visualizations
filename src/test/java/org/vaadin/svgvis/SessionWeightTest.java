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

        System.out.println("Size         | Raw Data       | DataPoints     | WindRose (16 sectors)");
        System.out.println("-------------|----------------|----------------|----------------------");

        for (int size : dataSizes) {
            if (size > allData.size()) continue;

            List<RawWeatherStationData> subset = allData.subList(allData.size() - size, allData.size());

            // Raw data
            int rawSize = measureSerializedSize(new ArrayList<>(subset));

            // DataPoints (what SparkLine stores)
            List<SvgSparkLine.DataPoint> points = subset.stream()
                    .map(d -> SvgSparkLine.DataPoint.of(d.getInstant(), d.getTempfC()))
                    .toList();
            int pointsSize = measureSerializedSize(new ArrayList<>(points));

            // WindRose aggregated data
            int sectors = 16;
            double[] duration = new double[sectors];
            for (RawWeatherStationData d : subset) {
                int sectorIndex = (int) Math.round(d.getWinddir() / 22.5) % sectors;
                duration[sectorIndex]++;
            }
            int windRoseSize = measureSerializedSize(duration);

            System.out.printf("%,12d | %14s | %14s | %s%n",
                    size, formatBytes(rawSize), formatBytes(pointsSize), formatBytes(windRoseSize));
        }

        System.out.println("\nKey insight: WindRose has O(1) session weight, SparkLine has O(n) weight.");
        System.out.println("For large datasets, consider aggregation strategies to reduce session size.");
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
