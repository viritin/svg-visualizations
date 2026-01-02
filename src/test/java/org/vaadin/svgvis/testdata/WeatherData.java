package org.vaadin.svgvis.testdata;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipInputStream;

/**
 * Loader for real weather station data exported from EclipseStore.
 * Data file contains ~948k records from Jan 2024 to Jan 2026 at 1-minute resolution.
 *
 * The data file is downloaded lazily from a remote URL if not present locally.
 */
public class WeatherData {

    private static final String DATA_FILE = "weather-data.ser";
    private static final String DATA_URL = "https://virit.in/weather-data.ser.zip";

    private static List<RawWeatherStationData> cachedData;

    /**
     * Custom ObjectInputStream that remaps the old package name to the new one.
     */
    private static class RemappingObjectInputStream extends ObjectInputStream {
        RemappingObjectInputStream(InputStream in) throws IOException {
            super(in);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            String name = desc.getName();
            // Remap old package to new package
            if (name.equals("com.test.requesteLogger.RawWeatherStationData")) {
                return RawWeatherStationData.class;
            }
            return super.resolveClass(desc);
        }
    }

    /**
     * Loads weather data from serialized file.
     * Downloads from remote URL if not present locally.
     * Data is cached after first load.
     */
    @SuppressWarnings("unchecked")
    public static synchronized List<RawWeatherStationData> load() {
        if (cachedData != null) {
            return cachedData;
        }

        Path dataFile = Path.of(DATA_FILE);

        // Download if not present
        if (!Files.exists(dataFile)) {
            downloadData(dataFile);
        }

        try (ObjectInputStream ois = new RemappingObjectInputStream(Files.newInputStream(dataFile))) {
            cachedData = (List<RawWeatherStationData>) ois.readObject();
            System.out.println("Loaded " + cachedData.size() + " weather records");
            return cachedData;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load weather data from " + DATA_FILE, e);
        }
    }

    /**
     * Downloads and extracts the weather data from remote ZIP file.
     */
    private static void downloadData(Path targetFile) {
        System.out.println("Downloading weather data from " + DATA_URL + "...");

        try (InputStream urlStream = URI.create(DATA_URL).toURL().openStream();
             BufferedInputStream bufferedStream = new BufferedInputStream(urlStream);
             ZipInputStream zipStream = new ZipInputStream(bufferedStream)) {

            // Move to first entry in ZIP
            var entry = zipStream.getNextEntry();
            if (entry == null) {
                throw new IOException("ZIP file is empty");
            }

            System.out.println("Extracting " + entry.getName() + " (" +
                    (entry.getSize() > 0 ? formatBytes(entry.getSize()) : "unknown size") + ")...");

            // Extract directly to target file
            Files.copy(zipStream, targetFile);

            System.out.println("Downloaded to " + targetFile.toAbsolutePath());

        } catch (IOException e) {
            throw new RuntimeException("Failed to download weather data from " + DATA_URL, e);
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /**
     * Returns the most recent N records.
     */
    public static List<RawWeatherStationData> getNewest(int count) {
        List<RawWeatherStationData> data = load();
        int start = Math.max(0, data.size() - count);
        return data.subList(start, data.size());
    }

    /**
     * Returns all records (about 948k).
     */
    public static List<RawWeatherStationData> getAll() {
        return load();
    }
}
