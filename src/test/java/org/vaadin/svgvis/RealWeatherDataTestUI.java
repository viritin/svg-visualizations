package org.vaadin.svgvis;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.Route;
import in.virit.color.NamedColor;
import in.virit.color.RgbColor;
import org.vaadin.firitin.components.button.VButton;
import org.vaadin.firitin.components.progressbar.VProgressBar;
import org.vaadin.svgvis.testdata.RawWeatherStationData;
import org.vaadin.svgvis.testdata.WeatherData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Supplier;

/**
 * Test UI with real weather data - approximately 2 years of 1-minute resolution data.
 */
@Route
public class RealWeatherDataTestUI extends VerticalLayout {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.of("UTC"));

    enum DataRange {
        DAY("Last Day", 1),
        WEEK("Last Week", 7),
        MONTH("Last Month", 30),
        ALL("All Data", -1);

        private final String label;
        private final int days;

        DataRange(String label, int days) {
            this.label = label;
            this.days = days;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private transient List<RawWeatherStationData> allData;
    private Div visualizationArea = new Div();
    private HorizontalLayout buttonBar = new HorizontalLayout();
    private Paragraph heapStats = new Paragraph();
    private Paragraph sessionWeight = new Paragraph();
    private Paragraph dataInfo = new Paragraph();
    private Paragraph interactionInfo = new Paragraph("Hover over charts or click WindRose sectors to see details");
    private HorizontalLayout statsBar;

    // Configuration
    private SvgSparkLine.Smoothing selectedSmoothing = SvgSparkLine.Smoothing.MOVING_AVERAGE;
    private DataRange selectedRange = DataRange.ALL;
    private Select<SvgSparkLine.Smoothing> smoothingSelect;
    private RadioButtonGroup<DataRange> rangeSelector;

    public RealWeatherDataTestUI() {
        setWidthFull();
        setPadding(true);
        setSpacing(true);

        add(new H2("Real Weather Data Visualization"));

        // Heap statistics - visible from start
        statsBar = createStatsBar();
        add(statsBar);

        VButton loadButton = new VButton("Load Data", e -> loadData());
        add(loadButton);

        visualizationArea.setWidthFull();
        add(visualizationArea);

        updateHeapStats();
    }

    private HorizontalLayout createStatsBar() {
        HorizontalLayout bar = new HorizontalLayout();
        bar.setAlignItems(Alignment.CENTER);
        bar.add(new VButton("Update Heap Stats", e -> updateHeapStats()));
        bar.add(heapStats);
        bar.add(new VButton("Estimate Session Weight", e -> estimateSessionWeight()));
        bar.add(sessionWeight);
        return bar;
    }

    private void estimateSessionWeight() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            LenientObjectOutputStream oos = new LenientObjectOutputStream(baos);

            // Serialize the visualization area to estimate its session footprint
            if (!visualizationArea.getChildren().findAny().isEmpty()) {
                oos.writeObject(visualizationArea);
                int vizSize = baos.size();
                int vizSkipped = oos.getSkippedCount();

                // Reset and measure the whole view
                baos.reset();
                oos = new LenientObjectOutputStream(baos);
                oos.writeObject(this);
                int viewSize = baos.size();
                int viewSkipped = oos.getSkippedCount();

                sessionWeight.setText("Session weight: viz=%s, view=%s (skipped %d/%d non-serializable)".formatted(
                        formatBytes(vizSize),
                        formatBytes(viewSize),
                        vizSkipped, viewSkipped
                ));
            } else {
                oos.writeObject(this);
                sessionWeight.setText("Session weight: view=%s (skipped %d non-serializable)".formatted(
                        formatBytes(baos.size()),
                        oos.getSkippedCount()
                ));
            }
            oos.close();
        } catch (Exception e) {
            sessionWeight.setText("Session weight: error - " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * ObjectOutputStream that skips non-serializable objects instead of throwing exceptions.
     */
    static class LenientObjectOutputStream extends ObjectOutputStream {
        private int skippedCount = 0;

        LenientObjectOutputStream(ByteArrayOutputStream out) throws IOException {
            super(out);
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object obj) {
            if (obj == null) {
                return null;
            }
            // Check if the object is serializable
            if (!(obj instanceof java.io.Serializable)) {
                skippedCount++;
                return null;
            }
            return obj;
        }

        int getSkippedCount() {
            return skippedCount;
        }
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

    private void loadData() {
        removeAll();
        add(new H2("Real Weather Data Visualization"));
        add(statsBar);
        updateHeapStats();
        add(VProgressBar.indeterminateForTask(() -> {
            long start = System.currentTimeMillis();
            allData = WeatherData.getAll();
            long loadTime = System.currentTimeMillis() - start;

            RawWeatherStationData first = allData.getFirst();
            RawWeatherStationData last = allData.getLast();

            removeAll();
            add(new H2("Real Weather Data Visualization"));
            add(statsBar);
            updateHeapStats();
            add(new Paragraph("Loaded %d records in %dms. Date range: %s to %s".formatted(
                    allData.size(), loadTime,
                    DATE_FORMAT.format(first.getInstant()),
                    DATE_FORMAT.format(last.getInstant())
            )));

            showConfigurationAndButtons();
        }));
    }

    private void showConfigurationAndButtons() {
        // Configuration row
        HorizontalLayout configBar = new HorizontalLayout();
        configBar.setAlignItems(Alignment.BASELINE);
        configBar.setSpacing(true);

        // Smoothing selector
        smoothingSelect = new Select<>();
        smoothingSelect.setLabel("Smoothing");
        smoothingSelect.setItems(SvgSparkLine.Smoothing.values());
        smoothingSelect.setValue(selectedSmoothing);
        smoothingSelect.addValueChangeListener(e -> selectedSmoothing = e.getValue());
        configBar.add(smoothingSelect);

        // Data range selector
        rangeSelector = new RadioButtonGroup<>();
        rangeSelector.setLabel("Data Range");
        rangeSelector.setItems(DataRange.values());
        rangeSelector.setValue(selectedRange);
        rangeSelector.addValueChangeListener(e -> {
            selectedRange = e.getValue();
            updateDataInfo();
        });
        configBar.add(rangeSelector);

        // Data info
        dataInfo.getStyle().setMarginLeft("20px");
        configBar.add(dataInfo);
        updateDataInfo();

        add(configBar);
        showVisualizationButtons();
    }

    private void updateDataInfo() {
        List<RawWeatherStationData> filtered = getFilteredData();
        if (filtered.isEmpty()) {
            dataInfo.setText("No data in selected range");
        } else {
            dataInfo.setText("Using %d records (%s to %s)".formatted(
                    filtered.size(),
                    DATE_FORMAT.format(filtered.getFirst().getInstant()),
                    DATE_FORMAT.format(filtered.getLast().getInstant())
            ));
        }
    }

    private List<RawWeatherStationData> getFilteredData() {
        if (selectedRange == DataRange.ALL || allData == null || allData.isEmpty()) {
            return allData;
        }

        Instant cutoff = allData.getLast().getInstant().minus(selectedRange.days, ChronoUnit.DAYS);
        return allData.stream()
                .filter(d -> d.getInstant().isAfter(cutoff))
                .toList();
    }

    private void showVisualizationButtons() {
        buttonBar = new HorizontalLayout();
        buttonBar.add(new ServerVisitMeasuringButton("Temperature", this::createTemperatureSection));
        buttonBar.add(new ServerVisitMeasuringButton("Wind Rose", this::createWindRoseSection));
        buttonBar.add(new ServerVisitMeasuringButton("Pressure", this::createPressureSection));
        buttonBar.add(new ServerVisitMeasuringButton("Solar Radiation", this::createSolarSection));
        buttonBar.add(new ServerVisitMeasuringButton("Humidity", this::createHumiditySection));
        buttonBar.add(new ServerVisitMeasuringButton("Wind Speed", this::createWindSpeedSection));
        add(buttonBar);

        visualizationArea = new Div();
        visualizationArea.setWidthFull();
        add(visualizationArea);

        interactionInfo.setId("interaction-info");
        interactionInfo.getStyle().set("font-style", "italic");
        add(interactionInfo);
    }

    /**
     * Button that measures the time from click to when DOM rendering is complete.
     * Displays the elapsed time in the button text after each click.
     */
    class ServerVisitMeasuringButton extends VButton {
        private final String label;
        private final Supplier<Component> visualizationSupplier;

        ServerVisitMeasuringButton(String label, Supplier<Component> visualizationSupplier) {
            super(label);
            this.label = label;
            this.visualizationSupplier = visualizationSupplier;

            addClickListener(e -> {
                long serverStartTime = System.currentTimeMillis();

                // Store the server timestamp on the element for JS to read
                getElement().setProperty("serverHitTime", serverStartTime);

                // Execute the visualization creation (this generates DOM updates)
                Component visualization = visualizationSupplier.get();
                showVisualization(visualization);

                long serverProcessingTime = System.currentTimeMillis() - serverStartTime;

                // Execute JS after DOM updates are sent to measure render time
                // Using double requestAnimationFrame to ensure painting is complete
                getElement().executeJs("""
                    const serverHitTime = $0;
                    const serverProcessingMs = $1;
                    const button = this;
                    const label = $2;

                    // Double rAF ensures we measure after browser has painted
                    requestAnimationFrame(() => {
                        requestAnimationFrame(() => {
                            const renderCompleteTime = performance.now();
                            const totalTimeMs = Date.now() - serverHitTime;
                            button.textContent = label + ' (server: ' + serverProcessingMs + 'ms, total: ' + totalTimeMs + 'ms)';
                        });
                    });
                """, serverStartTime, serverProcessingTime, label);
            });
        }
    }

    private void updateHeapStats() {
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        runtime.gc();
        runtime.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        long maxMb = runtime.maxMemory() / 1024 / 1024;
        long totalMb = runtime.totalMemory() / 1024 / 1024;
        long freeMb = runtime.freeMemory() / 1024 / 1024;
        long usedMb = totalMb - freeMb;

        heapStats.setText("Heap: %d MB used / %d MB allocated / %d MB max (%.1f%% of max)".formatted(
                usedMb, totalMb, maxMb, (usedMb * 100.0) / maxMb
        ));
    }

    private void showVisualization(Component visualization) {
        visualizationArea.removeAll();
        visualizationArea.add(visualization);
    }

    private Div createTemperatureSection() {
        List<RawWeatherStationData> data = getFilteredData();
        Div section = new Div();
        section.setWidthFull();
        section.add(new H3("Temperature (" + selectedRange + ")"));

        SvgSparkLine tempChart = new SvgSparkLine(100) {{
            setLineColor(new RgbColor(220, 60, 60));
            setTitle("Air Temperature °C");
            setSmoothing(selectedSmoothing);

            List<SvgSparkLine.DataPoint> points = data.stream()
                    .filter(RawWeatherStationData::hasValidAirTemp)
                    .map(d -> SvgSparkLine.DataPoint.of(d.getInstant(), d.getTempfC()))
                    .toList();

            setData(points);
            setTimeScale(
                    DATE_FORMAT.format(data.getFirst().getInstant()),
                    DATE_FORMAT.format(data.getLast().getInstant())
            );
            setCrosshairListener(pos -> {
                int idx = getIndexAt(pos);
                double val = getValueAt(pos);
                RawWeatherStationData record = data.get(Math.min(idx, data.size() - 1));
                interactionInfo.setText("Temperature: %.1f°C at %s (index %d of %d)".formatted(
                        val, DATE_FORMAT.format(record.getInstant()), idx, data.size()));
            });
        }};
        tempChart.setId("temp-sparkline");
        section.add(tempChart);

        return section;
    }

    private Div createWindRoseSection() {
        List<RawWeatherStationData> data = getFilteredData();
        Div section = new Div();
        section.add(new H3("Wind Direction Distribution (" + selectedRange + ")"));

        int sectors = 16;
        double[] duration = new double[sectors];
        double[] energy = new double[sectors];
        double degreesPerSector = 360.0 / sectors;

        for (RawWeatherStationData d : data) {
            int dir = d.getWinddir();
            int sectorIndex = (int) Math.round(dir / degreesPerSector) % sectors;
            duration[sectorIndex]++;
            double speed = d.getWindSpeedMs();
            energy[sectorIndex] += speed * speed * speed;
        }

        WindRose windRose = new WindRose(300) {{
            setTitle("Wind Distribution");
            addSeries("Duration", new RgbColor(100, 149, 237), duration);
            addSeries("Energy", new RgbColor(255, 140, 0), energy);
            setSectorClickListener(click -> {
                interactionInfo.setText("Wind Rose: %s (%d°) - Duration: %.0f min (%.1f%%), Energy: %.0f (%.1f%%)".formatted(
                        click.directionLabel(), click.centerDegrees(),
                        click.seriesValues().get(0), click.seriesPercentages().get(0),
                        click.seriesValues().get(1), click.seriesPercentages().get(1)));
            });
            draw();
        }};
        windRose.setId("wind-rose");
        section.add(windRose);

        return section;
    }

    private Div createPressureSection() {
        List<RawWeatherStationData> data = getFilteredData();
        Div section = new Div();
        section.setWidthFull();
        section.add(new H3("Barometric Pressure (" + selectedRange + ")"));

        SvgSparkLine pressureChart = new SvgSparkLine(100) {{
            setLineColor(new RgbColor(70, 130, 180));
            setTitle("Pressure hPa");
            setSmoothing(selectedSmoothing);

            List<SvgSparkLine.DataPoint> points = data.stream()
                    .map(d -> SvgSparkLine.DataPoint.of(d.getInstant(), d.getBaromRelHpa()))
                    .toList();

            setData(points);
            setTimeScale(
                    DATE_FORMAT.format(data.getFirst().getInstant()),
                    DATE_FORMAT.format(data.getLast().getInstant())
            );
            setCrosshairListener(pos -> {
                int idx = getIndexAt(pos);
                double val = getValueAt(pos);
                RawWeatherStationData record = data.get(Math.min(idx, data.size() - 1));
                interactionInfo.setText("Pressure: %.1f hPa at %s (index %d of %d)".formatted(
                        val, DATE_FORMAT.format(record.getInstant()), idx, data.size()));
            });
        }};
        pressureChart.setId("pressure-sparkline");
        section.add(pressureChart);

        return section;
    }

    private Div createSolarSection() {
        List<RawWeatherStationData> data = getFilteredData();
        Div section = new Div();
        section.setWidthFull();
        section.add(new H3("Solar Radiation (" + selectedRange + ")"));

        SvgSparkLine solarChart = new SvgSparkLine(100) {{
            setLineColor(NamedColor.ORANGE);
            setTitle("Solar W/m²");
            setSmoothing(selectedSmoothing);

            List<SvgSparkLine.DataPoint> points = data.stream()
                    .map(d -> SvgSparkLine.DataPoint.of(d.getInstant(), d.getSolarradiation()))
                    .toList();

            setData(points);
            setTimeScale(
                    DATE_FORMAT.format(data.getFirst().getInstant()),
                    DATE_FORMAT.format(data.getLast().getInstant())
            );
            setCrosshairListener(pos -> {
                int idx = getIndexAt(pos);
                double val = getValueAt(pos);
                RawWeatherStationData record = data.get(Math.min(idx, data.size() - 1));
                interactionInfo.setText("Solar: %.1f W/m² at %s (index %d of %d)".formatted(
                        val, DATE_FORMAT.format(record.getInstant()), idx, data.size()));
            });
        }};
        solarChart.setId("solar-sparkline");
        section.add(solarChart);

        return section;
    }

    private Div createHumiditySection() {
        List<RawWeatherStationData> data = getFilteredData();
        List<RawWeatherStationData> validData = data.stream()
                .filter(RawWeatherStationData::hasValidAirTemp)
                .toList();
        Div section = new Div();
        section.setWidthFull();
        section.add(new H3("Humidity (" + selectedRange + ")"));

        SvgSparkLine humidityChart = new SvgSparkLine(100) {{
            setLineColor(new RgbColor(60, 179, 113));
            setTitle("Humidity %");
            setSmoothing(selectedSmoothing);

            List<SvgSparkLine.DataPoint> points = validData.stream()
                    .map(d -> SvgSparkLine.DataPoint.of(d.getInstant(), d.getHumidity()))
                    .toList();

            setData(points);
            setTimeScale(
                    DATE_FORMAT.format(data.getFirst().getInstant()),
                    DATE_FORMAT.format(data.getLast().getInstant())
            );
            setCrosshairListener(pos -> {
                int idx = getIndexAt(pos);
                double val = getValueAt(pos);
                RawWeatherStationData record = validData.get(Math.min(idx, validData.size() - 1));
                interactionInfo.setText("Humidity: %.0f%% at %s (index %d of %d)".formatted(
                        val, DATE_FORMAT.format(record.getInstant()), idx, validData.size()));
            });
        }};
        humidityChart.setId("humidity-sparkline");
        section.add(humidityChart);

        return section;
    }

    private Div createWindSpeedSection() {
        List<RawWeatherStationData> data = getFilteredData();
        Div section = new Div();
        section.setWidthFull();
        section.add(new H3("Wind Speed (" + selectedRange + ")"));

        SvgSparkLine windChart = new SvgSparkLine(100) {{
            setLineColor(new RgbColor(147, 112, 219));
            setTitle("Wind m/s");
            setSmoothing(selectedSmoothing);

            List<SvgSparkLine.DataPoint> points = data.stream()
                    .map(d -> SvgSparkLine.DataPoint.of(d.getInstant(), d.getWindSpeedMs()))
                    .toList();

            setData(points);
            setTimeScale(
                    DATE_FORMAT.format(data.getFirst().getInstant()),
                    DATE_FORMAT.format(data.getLast().getInstant())
            );
            setCrosshairListener(pos -> {
                int idx = getIndexAt(pos);
                double val = getValueAt(pos);
                RawWeatherStationData record = data.get(Math.min(idx, data.size() - 1));
                interactionInfo.setText("Wind: %.1f m/s at %s (index %d of %d)".formatted(
                        val, DATE_FORMAT.format(record.getInstant()), idx, data.size()));
            });
        }};
        windChart.setId("wind-sparkline");
        section.add(windChart);

        return section;
    }
}
