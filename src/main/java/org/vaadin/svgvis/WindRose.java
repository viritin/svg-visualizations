package org.vaadin.svgvis;

import in.virit.color.Color;
import in.virit.color.RgbColor;
import org.vaadin.firitin.components.VSvg;
import org.vaadin.firitin.element.svg.CircleElement;
import org.vaadin.firitin.element.svg.LineElement;
import org.vaadin.firitin.element.svg.PathElement;
import org.vaadin.firitin.element.svg.TextElement;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A wind rose visualization showing directional distribution of data.
 * Supports multiple data series in a single graph.
 */
public class WindRose extends VSvg {

    public record DataSeries(String label, Color color, double[] values) implements java.io.Serializable {}

    /**
     * Data provided when a sector is clicked.
     */
    public record SectorClickData(
            int sectorIndex,
            String directionLabel,
            int centerDegrees,
            List<Double> seriesValues,
            List<Double> seriesPercentages
    ) {}

    private final int size;
    private final int sectors;
    private final List<DataSeries> seriesList = new ArrayList<>();
    private String title;
    private Consumer<SectorClickData> sectorClickListener;
    private PathElement highlightWedge;
    private boolean showSectorLines = true;

    public WindRose(int size, int sectors) {
        this.size = size;
        this.sectors = sectors;

        int totalSize = size + 40; // Extra space for labels
        getElement().setAttribute("viewBox", "0 0 %d %d".formatted(totalSize, totalSize));
        withSize(totalSize + "px", totalSize + "px");
    }

    public WindRose(int size) {
        this(size, 16);
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setSectorClickListener(Consumer<SectorClickData> listener) {
        this.sectorClickListener = listener;
    }

    public void setShowSectorLines(boolean show) {
        this.showSectorLines = show;
    }

    public int getSectors() {
        return sectors;
    }

    /**
     * Adds a data series to the wind rose.
     * Values array should have one value per sector.
     *
     * @param label display label for the legend
     * @param color color for this series
     * @param values array of values, one per sector (length must match sectors count)
     */
    public void addSeries(String label, Color color, double[] values) {
        if (values.length != sectors) {
            throw new IllegalArgumentException("Values array length (%d) must match sectors count (%d)"
                    .formatted(values.length, sectors));
        }
        seriesList.add(new DataSeries(label, color, values));
    }

    /**
     * Returns the current list of data series.
     */
    public List<DataSeries> getSeriesList() {
        return new ArrayList<>(seriesList);
    }

    /**
     * Clears all data series.
     */
    public void clearSeries() {
        seriesList.clear();
    }

    public void draw() {
        getElement().removeAllChildren();

        if (seriesList.isEmpty()) return;

        double centerX = size / 2.0 + 20;
        double centerY = size / 2.0 + 20;
        double maxRadius = size / 2.0 - 10;

        // Find global max value across all series for consistent scaling
        double globalMax = 0;
        for (DataSeries series : seriesList) {
            for (double v : series.values()) {
                globalMax = Math.max(globalMax, v);
            }
        }
        if (globalMax == 0) globalMax = 1;

        // Draw reference circles
        Color gridColor = new RgbColor(100, 100, 100);
        for (int i = 1; i <= 4; i++) {
            double r = maxRadius * i / 4;
            CircleElement circle = new CircleElement()
                    .center(centerX, centerY)
                    .r(r)
                    .noFill()
                    .stroke(gridColor)
                    .strokeWidth(0.5);
            getElement().appendChild(circle);
        }

        // Draw cardinal direction lines (only if sector lines are off) and labels
        String[] cardinals = {"N", "E", "S", "W"};
        for (int i = 0; i < 4; i++) {
            double angle = Math.toRadians(i * 90 - 90);
            double x2 = centerX + Math.cos(angle) * maxRadius;
            double y2 = centerY + Math.sin(angle) * maxRadius;

            if (!showSectorLines) {
                LineElement line = new LineElement()
                        .from(centerX, centerY)
                        .to(x2, y2)
                        .stroke(new RgbColor(80, 80, 80))
                        .strokeWidth(0.5);
                getElement().appendChild(line);
            }

            // Cardinal labels
            double labelX = centerX + Math.cos(angle) * (maxRadius + 12);
            double labelY = centerY + Math.sin(angle) * (maxRadius + 12);
            TextElement label = new TextElement(labelX, labelY + 4, cardinals[i])
                    .textAnchor(TextElement.TextAnchor.MIDDLE)
                    .fontSize(10)
                    .fill(new RgbColor(180, 180, 180));
            getElement().appendChild(label);
        }

        // Draw each series with different rendering styles
        double degreesPerSector = 360.0 / sectors;
        for (int seriesIndex = 0; seriesIndex < seriesList.size(); seriesIndex++) {
            DataSeries series = seriesList.get(seriesIndex);
            double[] values = series.values();

            // Find max for this series (for individual scaling)
            double seriesMax = 0;
            for (double v : values) {
                seriesMax = Math.max(seriesMax, v);
            }
            if (seriesMax == 0) continue;

            for (int i = 0; i < sectors; i++) {
                double value = values[i];
                if (value == 0) continue;

                double radius = (value / seriesMax) * maxRadius;
                double startAngle = Math.toRadians(i * degreesPerSector - degreesPerSector / 2 - 90);
                double endAngle = Math.toRadians(i * degreesPerSector + degreesPerSector / 2 - 90);

                double x1 = centerX + Math.cos(startAngle) * radius;
                double y1 = centerY + Math.sin(startAngle) * radius;
                double x2 = centerX + Math.cos(endAngle) * radius;
                double y2 = centerY + Math.sin(endAngle) * radius;

                PathElement wedge = new PathElement()
                        .moveTo(centerX, centerY)
                        .lineTo(x1, y1)
                        .arcTo(radius, radius, 0, false, true, x2, y2)
                        .closePath();

                if (seriesIndex == 0) {
                    // First series: filled wedges
                    wedge.fill(series.color())
                            .fillOpacity(0.4)
                            .stroke(series.color())
                            .strokeWidth(1);
                } else {
                    // Subsequent series: outline only with thicker stroke
                    wedge.noFill()
                            .stroke(series.color())
                            .strokeWidth(2);
                }

                getElement().appendChild(wedge);
            }
        }

        // Draw sector divider lines (hairlines)
        if (showSectorLines) {
            for (int i = 0; i < sectors; i++) {
                double angle = Math.toRadians(i * degreesPerSector - degreesPerSector / 2 - 90);
                double x2 = centerX + Math.cos(angle) * maxRadius;
                double y2 = centerY + Math.sin(angle) * maxRadius;

                LineElement sectorLine = new LineElement()
                        .from(centerX, centerY)
                        .to(x2, y2)
                        .stroke(new RgbColor(60, 60, 60))
                        .strokeWidth(0.5);
                getElement().appendChild(sectorLine);
            }
        }

        // Draw legend centered
        double legendY = size + 32;
        double itemSpacing = 70; // Space between legend items
        double totalLegendWidth = (seriesList.size() - 1) * itemSpacing;
        double legendStartX = centerX - totalLegendWidth / 2;

        for (int i = 0; i < seriesList.size(); i++) {
            DataSeries series = seriesList.get(i);
            double x = legendStartX + i * itemSpacing;

            // Color indicator
            CircleElement colorDot = new CircleElement()
                    .center(x - 4, legendY)
                    .r(4)
                    .fill(series.color());
            getElement().appendChild(colorDot);

            // Label
            TextElement legendLabel = new TextElement(x + 4, legendY + 3, series.label())
                    .textAnchor(TextElement.TextAnchor.START)
                    .fontSize(9)
                    .fill(new RgbColor(180, 180, 180));
            getElement().appendChild(legendLabel);
        }

        // Create highlight wedge (initially invisible, will be shown on click)
        highlightWedge = new PathElement()
                .fill(new RgbColor(255, 255, 255))
                .fillOpacity(0.25)
                .noStroke();
        highlightWedge.getStyle().set("pointer-events", "none");
        highlightWedge.setVisible(false);
        getElement().appendChild(highlightWedge);

        // Draw clickable overlay sectors (full radius, transparent)
        if (sectorClickListener != null) {
            // Calculate totals for each series
            double[] totals = new double[seriesList.size()];
            for (int s = 0; s < seriesList.size(); s++) {
                double[] values = seriesList.get(s).values();
                for (double v : values) {
                    totals[s] += v;
                }
            }

            for (int i = 0; i < sectors; i++) {
                double startAngle = Math.toRadians(i * degreesPerSector - degreesPerSector / 2 - 90);
                double endAngle = Math.toRadians(i * degreesPerSector + degreesPerSector / 2 - 90);

                double x1 = centerX + Math.cos(startAngle) * maxRadius;
                double y1 = centerY + Math.sin(startAngle) * maxRadius;
                double x2 = centerX + Math.cos(endAngle) * maxRadius;
                double y2 = centerY + Math.sin(endAngle) * maxRadius;

                PathElement clickableWedge = new PathElement()
                        .moveTo(centerX, centerY)
                        .lineTo(x1, y1)
                        .arcTo(maxRadius, maxRadius, 0, false, true, x2, y2)
                        .closePath()
                        .fill(new RgbColor(255, 255, 255))
                        .fillOpacity(0);

                clickableWedge.getStyle().setCursor("pointer");

                final int sectorIndex = i;
                final double hlX1 = x1, hlY1 = y1, hlX2 = x2, hlY2 = y2;
                final double[] seriesTotals = totals;

                clickableWedge.addEventListener("click", e -> {
                    // Update highlight wedge path
                    highlightWedge.clear()
                            .moveTo(centerX, centerY)
                            .lineTo(hlX1, hlY1)
                            .arcTo(maxRadius, maxRadius, 0, false, true, hlX2, hlY2)
                            .closePath();
                    highlightWedge.setVisible(true);

                    int centerDegrees = (int) Math.round(sectorIndex * degreesPerSector) % 360;
                    String dirLabel = getDirectionLabel(centerDegrees);

                    List<Double> seriesValues = new ArrayList<>();
                    List<Double> seriesPercentages = new ArrayList<>();
                    for (int s = 0; s < seriesList.size(); s++) {
                        double val = seriesList.get(s).values()[sectorIndex];
                        seriesValues.add(val);
                        seriesPercentages.add(seriesTotals[s] > 0 ? (val / seriesTotals[s]) * 100 : 0);
                    }

                    SectorClickData data = new SectorClickData(
                            sectorIndex, dirLabel, centerDegrees,
                            seriesValues, seriesPercentages
                    );
                    sectorClickListener.accept(data);
                });

                getElement().appendChild(clickableWedge);
            }
        }

        // Draw title
        if (title != null) {
            TextElement titleLabel = new TextElement(centerX, 12, title)
                    .textAnchor(TextElement.TextAnchor.MIDDLE)
                    .fontSize(11)
                    .fill(new RgbColor(200, 200, 200));
            getElement().appendChild(titleLabel);
        }
    }

    private String getDirectionLabel(int degrees) {
        // 16-point compass rose labels
        String[] directions = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        int index = (int) Math.round(degrees / 22.5) % 16;
        return directions[index];
    }
}
