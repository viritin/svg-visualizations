package org.vaadin.svgvis.testdata;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.util.List;

/**
 * Loader for real weather station data exported from EclipseStore.
 * Data file contains ~948k records from Jan 2024 to Jan 2026 at 1-minute resolution.
 */
public class WeatherData {

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
     * Data is cached after first load.
     */
    @SuppressWarnings("unchecked")
    public static synchronized List<RawWeatherStationData> load() {
        if (cachedData != null) {
            return cachedData;
        }

        try (ObjectInputStream ois = new RemappingObjectInputStream(new FileInputStream("weather-data.ser"))) {
            cachedData = (List<RawWeatherStationData>) ois.readObject();
            System.out.println("Loaded " + cachedData.size() + " weather records");
            return cachedData;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load weather data from weather-data.ser", e);
        }
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
