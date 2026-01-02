package org.vaadin.svgvis;

import com.vaadin.flow.component.AttachEvent;
import in.virit.color.Color;
import in.virit.color.NamedColor;
import org.vaadin.firitin.components.VSvg;
import org.vaadin.firitin.element.svg.LineElement;
import org.vaadin.firitin.element.svg.PathElement;
import org.vaadin.firitin.element.svg.PolylineElement;
import org.vaadin.firitin.element.svg.SvgGraphicsElement;
import org.vaadin.firitin.element.svg.TextElement;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A lightweight SVG-based sparkline/line chart component.
 * Supports multiple data series, smoothing algorithms, and interactive crosshair.
 */
public class SvgSparkLine extends VSvg {
    private final int height;
    private final int viewBoxWidth;
    private static final int fontSize = 10;
    private static final double RDP_EPSILON_BASE = 1.0;
    private final double rdpEpsilon;

    private List<DataPoint> dataPoints = new ArrayList<>();
    private Double fixedXMin = null;
    private Double fixedXMax = null;
    private Color lineColor = NamedColor.BLACK;
    private List<DataSeries> additionalSeries = new ArrayList<>();

    /**
     * Represents a data point with x position and y value.
     */
    public record DataPoint(double x, double y) implements java.io.Serializable {
        public static DataPoint of(Instant timestamp, double value) {
            return new DataPoint(timestamp.toEpochMilli(), value);
        }

        public static DataPoint of(double x, double y) {
            return new DataPoint(x, y);
        }
    }

    /**
     * Smoothing algorithm options for the sparkline data.
     */
    public enum Smoothing {
        NONE,
        RDP,
        MOVING_AVERAGE
    }

    private Smoothing smoothing = Smoothing.MOVING_AVERAGE;
    private static final int TARGET_POINTS = 50;
    private boolean useBezierCurve = true;

    /**
     * Represents an additional data series with its own color.
     */
    public record DataSeries(List<DataPoint> data, Color color) implements java.io.Serializable {}

    private String title;
    private String timeScaleStart;
    private String timeScaleEnd;
    private Consumer<Double> crosshairListener;
    private LineElement crosshairLine;
    private boolean crosshairEnabled = false;

    /**
     * Creates a sparkline with fixed pixel dimensions.
     */
    public SvgSparkLine(int width, int height) {
        this.viewBoxWidth = width;
        this.height = height - 2 * fontSize;
        this.rdpEpsilon = Math.max(RDP_EPSILON_BASE, width / 100.0);
        int totalHeight = this.height + 2 * fontSize;
        getElement().setAttribute("viewBox", "0 0 %d %d".formatted(width, totalHeight));
        getElement().setAttribute("preserveAspectRatio", "none");
        withSize(width + "px", totalHeight + "px");
    }

    /**
     * Creates a sparkline that fills available width (100%) with fixed height.
     */
    public SvgSparkLine(int height) {
        this.viewBoxWidth = 1000;
        this.height = height - 2 * fontSize;
        this.rdpEpsilon = 1.0;
        int totalHeight = this.height + 2 * fontSize;
        getElement().setAttribute("viewBox", "0 0 %d %d".formatted(viewBoxWidth, totalHeight));
        getElement().setAttribute("preserveAspectRatio", "none");
        setWidth("100%");
        setHeight(totalHeight + "px");
    }

    /**
     * Sets data from a list of DataPoints.
     * X positions will be normalized to 0.0-1.0 range.
     */
    public void setData(List<DataPoint> points) {
        this.dataPoints = normalizeDataPoints(points);
        this.additionalSeries.clear();
    }

    /**
     * Sets evenly distributed data (backward compatible).
     * Points are distributed uniformly across the x-axis using index as x.
     */
    public void setData(double... values) {
        List<DataPoint> points = new ArrayList<>(values.length);
        for (int i = 0; i < values.length; i++) {
            points.add(new DataPoint(i, values[i]));
        }
        this.dataPoints = normalizeDataPoints(points);
        this.additionalSeries.clear();
    }

    /**
     * Sets data with explicit x positions.
     */
    public void setData(double[] xPositions, double[] values) {
        if (xPositions.length != values.length) {
            throw new IllegalArgumentException("xPositions and values must have the same length");
        }
        List<DataPoint> points = new ArrayList<>(values.length);
        for (int i = 0; i < values.length; i++) {
            points.add(new DataPoint(xPositions[i], values[i]));
        }
        this.dataPoints = normalizeDataPoints(points);
        this.additionalSeries.clear();
    }

    /**
     * Sets data with timestamps as x positions.
     */
    public void setData(Instant[] timestamps, double[] values) {
        if (timestamps.length != values.length) {
            throw new IllegalArgumentException("timestamps and values must have the same length");
        }
        List<DataPoint> points = new ArrayList<>(values.length);
        for (int i = 0; i < values.length; i++) {
            points.add(DataPoint.of(timestamps[i], values[i]));
        }
        this.dataPoints = normalizeDataPoints(points);
        this.additionalSeries.clear();
    }

    /**
     * Normalizes x positions to 0.0-1.0 range.
     * Uses fixed x range if set, otherwise auto-fits to data range.
     */
    private List<DataPoint> normalizeDataPoints(List<DataPoint> points) {
        if (points.isEmpty()) {
            return new ArrayList<>();
        }

        double minX, maxX;
        if (fixedXMin != null && fixedXMax != null) {
            // Use fixed range
            minX = fixedXMin;
            maxX = fixedXMax;
        } else {
            // Auto-fit to data range
            minX = points.getFirst().x();
            maxX = points.getLast().x();
        }

        double range = maxX - minX;

        if (range == 0) {
            // All same x position or invalid range, distribute evenly
            List<DataPoint> normalized = new ArrayList<>(points.size());
            for (int i = 0; i < points.size(); i++) {
                double x = points.size() > 1 ? (double) i / (points.size() - 1) : 0.0;
                normalized.add(new DataPoint(x, points.get(i).y()));
            }
            return normalized;
        }

        List<DataPoint> normalized = new ArrayList<>(points.size());
        for (DataPoint p : points) {
            normalized.add(new DataPoint((p.x() - minX) / range, p.y()));
        }
        return normalized;
    }

    /**
     * Adds an additional data series to be drawn with a different color.
     */
    public void addSeries(List<DataPoint> data, Color color) {
        additionalSeries.add(new DataSeries(normalizeDataPoints(data), color));
    }

    /**
     * Adds an additional data series (legacy API).
     */
    public void addSeries(double[] values, Color color) {
        List<DataPoint> points = new ArrayList<>(values.length);
        for (int i = 0; i < values.length; i++) {
            points.add(new DataPoint(i, values[i]));
        }
        additionalSeries.add(new DataSeries(normalizeDataPoints(points), color));
    }

    public void setLineColor(Color lineColor) {
        this.lineColor = lineColor;
    }

    public Color getLineColor() {
        return lineColor;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public void setSmoothing(Smoothing smoothing) {
        this.smoothing = smoothing;
    }

    public void setUseBezierCurve(boolean useBezier) {
        this.useBezierCurve = useBezier;
    }

    public boolean isUseBezierCurve() {
        return useBezierCurve;
    }

    public Smoothing getSmoothing() {
        return smoothing;
    }

    public int getViewBoxWidth() {
        return viewBoxWidth;
    }

    public void setTimeScale(String start, String end) {
        this.timeScaleStart = start;
        this.timeScaleEnd = end;
    }

    /**
     * Sets a fixed x-axis range. Data points outside this range will still be rendered
     * but may appear beyond the graph boundaries.
     * When set, data points are positioned relative to this range instead of auto-fitting.
     * @param min minimum x value (left edge of graph)
     * @param max maximum x value (right edge of graph)
     */
    public void setXRange(double min, double max) {
        this.fixedXMin = min;
        this.fixedXMax = max;
    }

    /**
     * Sets a fixed x-axis range using timestamps.
     * @param start start time (left edge of graph)
     * @param end end time (right edge of graph)
     */
    public void setXRange(Instant start, Instant end) {
        this.fixedXMin = (double) start.toEpochMilli();
        this.fixedXMax = (double) end.toEpochMilli();
    }

    /**
     * Clears the fixed x-axis range, reverting to auto-fit behavior.
     */
    public void clearXRange() {
        this.fixedXMin = null;
        this.fixedXMax = null;
    }

    public void setCrosshairListener(Consumer<Double> listener) {
        this.crosshairListener = listener;
        this.crosshairEnabled = listener != null;
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        draw();
        if (crosshairEnabled) {
            setupCrosshairEvents();
        }
        setupTextScaling();
    }

    private void setupTextScaling() {
        getElement().executeJs("""
            const svg = this;
            const viewBoxWidth = %d;
            const viewBoxHeight = %d;

            function updateTextScale() {
                const rect = svg.getBoundingClientRect();
                if (rect.width === 0 || rect.height === 0) return;

                const scaleX = rect.width / viewBoxWidth;
                const scaleY = rect.height / viewBoxHeight;
                const inverseScaleX = scaleY / scaleX;

                svg.querySelectorAll('text').forEach(text => {
                    const x = text.getAttribute('x') || 0;
                    text.style.transformOrigin = x + 'px center';
                    text.style.transform = 'scaleX(' + inverseScaleX + ')';
                });
            }

            new ResizeObserver(updateTextScale).observe(svg);
            svg._updateTextScale = updateTextScale;
            requestAnimationFrame(updateTextScale);
            """.formatted(viewBoxWidth, height + 2 * fontSize));
    }

    private void setupCrosshairEvents() {
        String updateCrosshairJs = """
            const svg = this;
            const line = svg.querySelector('line:last-of-type');
            if (!line) return;
            event.preventDefault();
            const rect = svg.getBoundingClientRect();
            const viewBoxWidth = %d;
            let offsetX;
            if (event.type === 'touchmove' || event.type === 'touchstart') {
                offsetX = event.touches[0].clientX - rect.left;
            } else {
                offsetX = event.offsetX;
            }
            const relPos = Math.max(0, Math.min(1, offsetX / rect.width));
            const x = relPos * viewBoxWidth;
            line.setAttribute('x1', x);
            line.setAttribute('x2', x);
            line.setAttribute('visibility', 'visible');
            """.formatted(viewBoxWidth);

        getElement().executeJs(
                "this.addEventListener('mousemove', function(event) { " + updateCrosshairJs + " })");
        getElement().addEventListener("mousemove", e -> {
            double offsetX = e.getEventData().get("event.offsetX").asDouble();
            double width = e.getEventData().get("element.clientWidth").asDouble();
            double relPos = Math.max(0, Math.min(1, offsetX / width));
            if (crosshairListener != null) {
                crosshairListener.accept(relPos);
            }
        }).debounce(300).addEventData("event.offsetX").addEventData("element.clientWidth");

        getElement().executeJs(
                "this.addEventListener('touchmove', function(event) { " + updateCrosshairJs + " }, {passive: false})");
        getElement().addEventListener("touchmove", e -> {
            double touchX = e.getEventData().get("event.touches[0].clientX").asDouble();
            double rectLeft = e.getEventData().get("element.getBoundingClientRect().left").asDouble();
            double width = e.getEventData().get("element.clientWidth").asDouble();
            double offsetX = touchX - rectLeft;
            double relPos = Math.max(0, Math.min(1, offsetX / width));
            if (crosshairListener != null) {
                crosshairListener.accept(relPos);
            }
        }).debounce(300).addEventData("event.touches[0].clientX")
          .addEventData("element.getBoundingClientRect().left")
          .addEventData("element.clientWidth");

        getElement().executeJs(
                "this.addEventListener('touchstart', function(event) { " + updateCrosshairJs + " }, {passive: false})");
        getElement().addEventListener("touchstart", e -> {
            double touchX = e.getEventData().get("event.touches[0].clientX").asDouble();
            double rectLeft = e.getEventData().get("element.getBoundingClientRect().left").asDouble();
            double width = e.getEventData().get("element.clientWidth").asDouble();
            double offsetX = touchX - rectLeft;
            double relPos = Math.max(0, Math.min(1, offsetX / width));
            if (crosshairListener != null) {
                crosshairListener.accept(relPos);
            }
        }).addEventData("event.touches[0].clientX")
          .addEventData("element.getBoundingClientRect().left")
          .addEventData("element.clientWidth");

        getElement().executeJs(
                "this.addEventListener('click', function(event) { " + updateCrosshairJs + " })");
        getElement().addEventListener("click", e -> {
            double offsetX = e.getEventData().get("event.offsetX").asDouble();
            double width = e.getEventData().get("element.clientWidth").asDouble();
            double relPos = Math.max(0, Math.min(1, offsetX / width));
            if (crosshairListener != null) {
                crosshairListener.accept(relPos);
            }
        }).addEventData("event.offsetX")
          .addEventData("element.clientWidth");
    }

    public void draw() {
        getElement().removeAllChildren();

        if (dataPoints.isEmpty()) return;

        // Apply smoothing to primary data BEFORE computing min/max
        // This reduces memory footprint and ensures crosshair uses displayed values
        if (smoothing == Smoothing.MOVING_AVERAGE) {
            dataPoints = applyMovingAverage(dataPoints);
        } else if (smoothing == Smoothing.RDP) {
            dataPoints = applyRdpToDataPoints(dataPoints);
        }

        // Apply smoothing to additional series
        List<DataSeries> smoothedSeries = new ArrayList<>();
        for (DataSeries series : additionalSeries) {
            List<DataPoint> smoothedData = switch (smoothing) {
                case MOVING_AVERAGE -> applyMovingAverage(series.data());
                case RDP -> applyRdpToDataPoints(series.data());
                default -> series.data();
            };
            smoothedSeries.add(new DataSeries(smoothedData, series.color()));
        }
        additionalSeries = smoothedSeries;

        // Compute global min/max across primary and all additional series
        double min = dataPoints.getFirst().y();
        double max = dataPoints.getFirst().y();
        for (DataPoint dp : dataPoints) {
            if (dp.y() < min) min = dp.y();
            if (dp.y() > max) max = dp.y();
        }
        for (DataSeries series : additionalSeries) {
            for (DataPoint dp : series.data()) {
                if (dp.y() < min) min = dp.y();
                if (dp.y() > max) max = dp.y();
            }
        }

        double minY = height + fontSize;
        double maxY = fontSize;

        LineElement minLine = new LineElement()
                .from(0, minY)
                .to(viewBoxWidth, minY)
                .stroke(lineColor)
                .strokeWidth(1)
                .strokeDasharray(2, 2);

        LineElement maxLine = new LineElement()
                .from(0, maxY)
                .to(viewBoxWidth, maxY)
                .stroke(lineColor)
                .strokeWidth(1)
                .strokeDasharray(2, 2);

        getElement().appendChild(minLine);
        getElement().appendChild(maxLine);

        // Draw additional series first (so primary is on top)
        final double finalMin = min;
        final double finalMax = max;
        for (DataSeries series : additionalSeries) {
            getElement().appendChild(createLineFromSmoothed(series.data(), series.color(), finalMin, finalMax));
        }

        // Draw primary series
        getElement().appendChild(createLineFromSmoothed(dataPoints, lineColor, min, max));

        // Labels
        TextElement minLabel = new TextElement(0, minY - 2, String.format("%.1f", min))
                .fontSize(fontSize)
                .fontWeight(TextElement.FontWeight.BOLD)
                .fill(lineColor);

        TextElement maxLabel = new TextElement(0, maxY - 2, String.format("%.1f", max))
                .fontSize(fontSize)
                .fontWeight(TextElement.FontWeight.BOLD)
                .fill(lineColor);

        getElement().appendChild(minLabel);
        getElement().appendChild(maxLabel);

        if (title != null) {
            TextElement titleLabel = new TextElement(viewBoxWidth, fontSize - 2.5, title)
                    .fontSize(fontSize)
                    .fontWeight(TextElement.FontWeight.BOLD)
                    .textAnchor(TextElement.TextAnchor.END)
                    .fill(lineColor);
            getElement().appendChild(titleLabel);
        }

        if (timeScaleStart != null) {
            TextElement startLabel = new TextElement(0, height + 2 * fontSize, timeScaleStart)
                    .fontSize(fontSize)
                    .textAnchor(TextElement.TextAnchor.START)
                    .fill(lineColor);
            getElement().appendChild(startLabel);
        }
        if (timeScaleEnd != null) {
            TextElement endLabel = new TextElement(viewBoxWidth, height + 2 * fontSize, timeScaleEnd)
                    .fontSize(fontSize)
                    .textAnchor(TextElement.TextAnchor.END)
                    .fill(lineColor);
            getElement().appendChild(endLabel);
        }

        if (crosshairEnabled) {
            crosshairLine = new LineElement()
                    .from(0, fontSize)
                    .to(0, height + fontSize)
                    .stroke(NamedColor.GRAY)
                    .strokeWidth(1);
            crosshairLine.setAttribute("visibility", "hidden");
            getElement().appendChild(crosshairLine);
        }

        getElement().executeJs("if(this._updateTextScale) requestAnimationFrame(this._updateTextScale)");

        // Clear data after drawing - SVG elements are already created,
        // data is no longer needed and would only consume session memory
        dataPoints = List.of();
        additionalSeries = List.of();
    }

    private SvgGraphicsElement createLineFromSmoothed(List<DataPoint> seriesData, Color color, double min, double max) {
        // Convert to screen coordinates (data is already smoothed/reduced)
        List<double[]> points = new ArrayList<>(seriesData.size());
        for (DataPoint dp : seriesData) {
            double x = dp.x() * viewBoxWidth;
            double y = height - (dp.y() - min) / (max - min) * height + fontSize;
            points.add(new double[]{x, y});
        }

        if (useBezierCurve && points.size() >= 2) {
            return createBezierPath(points, color);
        } else {
            return createPolyline(points, color);
        }
    }

    private PolylineElement createPolyline(List<double[]> points, Color color) {
        PolylineElement polyline = new PolylineElement()
                .noFill()
                .stroke(color);
        for (double[] point : points) {
            polyline.addPoint(round(point[0]), round(point[1]));
        }
        return polyline;
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private PathElement createBezierPath(List<double[]> points, Color color) {
        PathElement path = new PathElement()
                .noFill()
                .stroke(color);

        if (points.isEmpty()) return path;

        double[] first = points.getFirst();
        path.moveTo(round(first[0]), round(first[1]));

        if (points.size() == 1) return path;

        if (points.size() == 2) {
            double[] second = points.get(1);
            path.lineTo(round(second[0]), round(second[1]));
            return path;
        }

        for (int i = 0; i < points.size() - 1; i++) {
            double[] p0 = points.get(Math.max(0, i - 1));
            double[] p1 = points.get(i);
            double[] p2 = points.get(i + 1);
            double[] p3 = points.get(Math.min(points.size() - 1, i + 2));

            double tension = 6.0;
            double cp1x = round(p1[0] + (p2[0] - p0[0]) / tension);
            double cp1y = round(p1[1] + (p2[1] - p0[1]) / tension);
            double cp2x = round(p2[0] - (p3[0] - p1[0]) / tension);
            double cp2y = round(p2[1] - (p3[1] - p1[1]) / tension);

            path.cubicBezierTo(cp1x, cp1y, cp2x, cp2y, round(p2[0]), round(p2[1]));
        }

        return path;
    }

    /**
     * Applies moving average and downsamples data to approximately TARGET_POINTS.
     * Groups data points by x-position buckets and averages each bucket.
     * Works correctly with incomplete data that doesn't span the full x-range.
     */
    private List<DataPoint> applyMovingAverage(List<DataPoint> data) {
        if (data.size() <= TARGET_POINTS) {
            return data;
        }

        // Find actual data range (may be smaller than 0-1 if using fixed xRange with incomplete data)
        double dataMinX = data.getFirst().x();
        double dataMaxX = data.getFirst().x();
        for (DataPoint dp : data) {
            if (dp.x() < dataMinX) dataMinX = dp.x();
            if (dp.x() > dataMaxX) dataMaxX = dp.x();
        }
        double dataRange = dataMaxX - dataMinX;

        // If data range is too small, return original
        if (dataRange < 0.01) {
            return data;
        }

        // Calculate number of buckets proportional to the data range
        int numBuckets = Math.max(3, (int) (TARGET_POINTS * dataRange));
        double bucketWidth = dataRange / numBuckets;

        List<DataPoint> result = new ArrayList<>(numBuckets);

        for (int i = 0; i < numBuckets; i++) {
            double bucketStart = dataMinX + i * bucketWidth;
            double bucketEnd = dataMinX + (i + 1) * bucketWidth;
            double bucketCenter = (bucketStart + bucketEnd) / 2;

            // Find all points in this bucket
            double sum = 0;
            int count = 0;
            for (DataPoint dp : data) {
                if (dp.x() >= bucketStart && dp.x() < bucketEnd) {
                    sum += dp.y();
                    count++;
                }
            }

            if (count > 0) {
                result.add(new DataPoint(bucketCenter, sum / count));
            }
        }

        // If we got too few points (very sparse data), use original
        if (result.size() < 3) {
            return data;
        }

        return result;
    }

    /**
     * Applies RDP algorithm to reduce data points while preserving shape.
     * Works on normalized DataPoints (x in 0-1 range).
     */
    private List<DataPoint> applyRdpToDataPoints(List<DataPoint> data) {
        if (data.size() < 3) {
            return data;
        }

        // Find min/max y for normalization (to make epsilon scale-independent)
        double minY = data.stream().mapToDouble(DataPoint::y).min().orElse(0);
        double maxY = data.stream().mapToDouble(DataPoint::y).max().orElse(1);
        double rangeY = maxY - minY;
        if (rangeY < 0.001) rangeY = 1;

        // Convert to double[] with normalized y values
        List<double[]> points = new ArrayList<>(data.size());
        for (DataPoint dp : data) {
            double normalizedY = (dp.y() - minY) / rangeY;
            points.add(new double[]{dp.x(), normalizedY});
        }

        // Apply RDP with epsilon relative to normalized scale
        double normalizedEpsilon = rdpEpsilon / viewBoxWidth;
        List<double[]> reduced = ramerDouglasPeucker(points, normalizedEpsilon);

        // Convert back to DataPoints with original y values
        List<DataPoint> result = new ArrayList<>(reduced.size());
        for (double[] pt : reduced) {
            double originalY = pt[1] * rangeY + minY;
            result.add(new DataPoint(pt[0], originalY));
        }

        return result;
    }

    private List<double[]> ramerDouglasPeucker(List<double[]> points, double epsilon) {
        if (points.size() < 3) {
            return new ArrayList<>(points);
        }

        double maxDist = 0;
        int maxIndex = 0;
        double[] first = points.getFirst();
        double[] last = points.getLast();

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
}
