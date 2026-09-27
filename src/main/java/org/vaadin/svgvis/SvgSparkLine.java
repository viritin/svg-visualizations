package org.vaadin.svgvis;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import in.virit.color.Color;
import org.vaadin.firitin.components.VSvg;
import org.vaadin.firitin.element.svg.LineElement;
import org.vaadin.firitin.element.svg.PathElement;
import org.vaadin.firitin.element.svg.SvgGraphicsElement;
import org.vaadin.firitin.element.svg.TextElement;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * A lightweight SVG-based sparkline/line chart component.
 * Supports multiple data series, smoothing algorithms, and interactive crosshair.
 * <p>
 * Changes are drawn by themselves before the next response to the browser, so
 * there is no need to call {@link #draw()}. The data is dropped after drawing to
 * save session memory, so settings changed later, such as the title, show with
 * the next data. Without an explicit color everything
 * is drawn in {@code currentColor}, following the page's theme, with the grid
 * and axis labels fainter than the data. The parts carry stable class names for
 * page CSS: {@code sparkline-line} (the primary series), {@code sparkline-series}
 * (additional series), {@code sparkline-grid}, {@code sparkline-reference},
 * {@code sparkline-gap} (the dashed bridge over a gap in the data),
 * {@code sparkline-label} (with {@code sparkline-line-label} or
 * {@code sparkline-series-label} on the scale labels), {@code sparkline-title}
 * and {@code sparkline-crosshair}.
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
    /** Null draws in currentColor, i.e. whatever colour the page gives the text around it. */
    private Color lineColor;
    private List<DataSeries> additionalSeries = new ArrayList<>();
    private final List<ReferenceLine> referenceLines = new ArrayList<>();

    /**
     * Opacity used for the faint default color of reference lines.
     */
    private static final double REFERENCE_LINE_OPACITY = 0.5;
    private static final double GRID_OPACITY = 0.25;
    private static final double AXIS_LABEL_OPACITY = 0.7;
    private static final double DATA_STROKE_WIDTH = 1.5;

    private boolean dirty = true;
    private boolean drawScheduled;
    /** The drawn data was dropped to save memory; only new data can be drawn again. */
    private boolean dataDropped;

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
        /** Every point as it is. */
        NONE,
        /** Ramer–Douglas–Peucker: drops points that the line would pass anyway. */
        RDP,
        /**
         * Averages of fixed buckets; smooths noise, but the curve ends at the
         * middle of the last bucket and so lags behind the latest readings.
         */
        MOVING_AVERAGE,
        /**
         * Largest-Triangle-Three-Buckets, the default: keeps the actual points
         * that best preserve the shape, peaks included, and always the first and
         * the last, so the curve ends at the latest reading.
         */
        LTTB
    }

    private Smoothing smoothing = Smoothing.LTTB;
    private static final int TARGET_POINTS = 50;
    /** Points LTTB keeps: a few pixels apart on a card-wide chart. */
    private static final int LTTB_POINTS = 150;
    private static final double GAP_OPACITY = 0.4;
    private boolean useBezierCurve = true;

    /**
     * Represents an additional data series with its own color.
     */
    public record DataSeries(List<DataPoint> data, Color color, boolean ownScale, String unit)
            implements java.io.Serializable {
        /** A series drawn against the primary series' scale. */
        public DataSeries(List<DataPoint> data, Color color) {
            this(data, color, false, null);
        }
    }

    /**
     * A fixed horizontal reference line at a given y value (e.g. a target temperature).
     * A {@code null} color means the line is rendered with the faint default color
     * (the current line color at {@link #REFERENCE_LINE_OPACITY} opacity). A {@code null}
     * label means no text is drawn next to the line.
     */
    public record ReferenceLine(double value, Color color, String label) implements java.io.Serializable {}

    private String title;
    private String unit;
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
        this.height = height - 3 * fontSize;
        this.rdpEpsilon = Math.max(RDP_EPSILON_BASE, width / 100.0);
        int totalHeight = this.height + 3 * fontSize;
        getElement().setAttribute("viewBox", "0 0 %d %d".formatted(width, totalHeight));
        getElement().setAttribute("preserveAspectRatio", "none");
        withSize(width + "px", totalHeight + "px");
    }

    /**
     * Creates a sparkline that fills available width (100%) with fixed height.
     */
    public SvgSparkLine(int height) {
        this.viewBoxWidth = 1000;
        this.height = height - 3 * fontSize;
        this.rdpEpsilon = 1.0;
        int totalHeight = this.height + 3 * fontSize;
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
        this.additionalSeries = new ArrayList<>();
        dataDropped = false;
        changed();
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
        this.additionalSeries = new ArrayList<>();
        dataDropped = false;
        changed();
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
        this.additionalSeries = new ArrayList<>();
        dataDropped = false;
        changed();
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
        this.additionalSeries = new ArrayList<>();
        dataDropped = false;
        changed();
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
        changed();
    }

    /**
     * Adds a series drawn against a scale of its own, for a second quantity in
     * other units, e.g. air pressure next to humidity. Its min and max are
     * labelled at the right edge, in the series' colour and with its unit, while
     * the primary series' scale stays at the left. Only the first such series
     * gets labels.
     *
     * @param data  the points, with the same kind of x as the primary series
     * @param color the colour of the line and its labels, or null for currentColor
     *              (then style it with the {@code sparkline-series} and
     *              {@code sparkline-series-label} classes)
     * @param unit  appended to the scale labels as is (include the space if you
     *              want one), or null for none
     */
    public void addSeriesWithOwnScale(List<DataPoint> data, Color color, String unit) {
        additionalSeries.add(new DataSeries(normalizeDataPoints(data), color, true, unit));
        changed();
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
        changed();
    }

    /**
     * Adds a fixed horizontal reference line at the given y value (e.g. a target
     * temperature) using the faint default color: the current line color at
     * {@value #REFERENCE_LINE_OPACITY} opacity. The reference line expands the
     * y-axis scale when needed so that it is always visible.
     * <p>
     * Reference lines are persistent configuration and are not cleared by
     * {@link #draw()} (unlike data series). Use {@link #clearReferenceLines()}
     * to remove them.
     *
     * @param value the y value where the line is drawn
     */
    public void addReferenceLine(double value) {
        referenceLines.add(new ReferenceLine(value, null, null));
        changed();
    }

    /**
     * Adds a fixed horizontal reference line at the given y value using the faint
     * default color, with a text label (e.g. a target reading) drawn at the right
     * edge next to the line.
     *
     * @param value the y value where the line is drawn
     * @param label the text drawn next to the line, or {@code null} for none
     */
    public void addReferenceLine(double value, String label) {
        referenceLines.add(new ReferenceLine(value, null, label));
        changed();
    }

    /**
     * Adds a fixed horizontal reference line at the given y value with an explicit
     * color. The reference line expands the y-axis scale when needed so that it is
     * always visible.
     *
     * @param value the y value where the line is drawn
     * @param color the stroke color of the line
     */
    public void addReferenceLine(double value, Color color) {
        referenceLines.add(new ReferenceLine(value, color, null));
        changed();
    }

    /**
     * Adds a fixed horizontal reference line at the given y value with an explicit
     * color and a text label (e.g. a target reading) drawn at the right edge next
     * to the line.
     *
     * @param value the y value where the line is drawn
     * @param color the stroke color of the line and label
     * @param label the text drawn next to the line, or {@code null} for none
     */
    public void addReferenceLine(double value, Color color, String label) {
        referenceLines.add(new ReferenceLine(value, color, label));
        changed();
    }

    /**
     * Removes all reference lines.
     */
    public void clearReferenceLines() {
        referenceLines.clear();
        changed();
    }

    public void setLineColor(Color lineColor) {
        this.lineColor = lineColor;
        changed();
    }

    public Color getLineColor() {
        return lineColor;
    }

    /**
     * A unit appended to the primary scale's min and max labels as is, e.g.
     * {@code " °C"} (include the space if you want one), or null for none.
     */
    public void setUnit(String unit) {
        this.unit = unit;
        changed();
    }

    public void setTitle(String title) {
        this.title = title;
        changed();
    }

    public String getTitle() {
        return title;
    }

    public void setSmoothing(Smoothing smoothing) {
        this.smoothing = smoothing;
        changed();
    }

    public void setUseBezierCurve(boolean useBezier) {
        this.useBezierCurve = useBezier;
        changed();
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
        changed();
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
        changed();
    }

    /**
     * Draws the chart before the next response to the browser, once however many
     * changes come before it. Detached, it is drawn when attached.
     */
    private void changed() {
        dirty = true;
        if (!drawScheduled) {
            getUI().ifPresent(ui -> {
                drawScheduled = true;
                ui.beforeClientResponse(this, context -> {
                    drawScheduled = false;
                    drawIfPossible();
                });
            });
        }
    }

    /**
     * Draws pending changes, unless the data they would be drawn with is gone:
     * a setting changed after drawing (a title, say) would otherwise wipe the
     * chart. Such a change shows with the next data.
     */
    private void drawIfPossible() {
        if (dirty && !dataDropped) {
            draw();
        }
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        // The drawn elements survive a detach; the data does not (draw() drops it),
        // so drawing again on every attach wiped a chart that was moved or re-shown
        drawIfPossible();
        if (crosshairEnabled) {
            setupCrosshairEvents();
        }
        setupTextScaling();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        // A draw scheduled for a response that no longer includes this chart never runs
        drawScheduled = false;
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
            """.formatted(viewBoxWidth, height + 3 * fontSize));
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

    /**
     * Draws the chart now. Rarely needed, as changes are drawn by themselves
     * before the next response; after drawing, the data is dropped to save
     * session memory.
     */
    public void draw() {
        dirty = false;
        getElement().removeAllChildren();

        if (dataPoints.isEmpty()) return;

        // Gaps come from the data as it is: a downsampler's points are unevenly spaced
        List<double[]> primaryGaps = gapsIn(dataPoints);
        List<List<double[]>> seriesGaps = new ArrayList<>();
        for (DataSeries series : additionalSeries) {
            seriesGaps.add(gapsIn(series.data()));
        }

        // Apply smoothing to primary data BEFORE computing min/max
        // This reduces memory footprint and ensures crosshair uses displayed values
        if (smoothing == Smoothing.MOVING_AVERAGE) {
            dataPoints = applyMovingAverage(dataPoints);
        } else if (smoothing == Smoothing.RDP) {
            dataPoints = applyRdpToDataPoints(dataPoints);
        } else if (smoothing == Smoothing.LTTB) {
            dataPoints = SparkLineGeometry.lttb(dataPoints, LTTB_POINTS);
        }

        // Apply smoothing to additional series
        List<DataSeries> smoothedSeries = new ArrayList<>();
        for (DataSeries series : additionalSeries) {
            List<DataPoint> smoothedData = switch (smoothing) {
                case MOVING_AVERAGE -> applyMovingAverage(series.data());
                case RDP -> applyRdpToDataPoints(series.data());
                case LTTB -> SparkLineGeometry.lttb(series.data(), LTTB_POINTS);
                default -> series.data();
            };
            smoothedSeries.add(new DataSeries(smoothedData, series.color(), series.ownScale(), series.unit()));
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
            if (series.ownScale()) {
                continue;
            }
            for (DataPoint dp : series.data()) {
                if (dp.y() < min) min = dp.y();
                if (dp.y() > max) max = dp.y();
            }
        }

        // Expand the y-axis scale to keep reference lines visible
        for (ReferenceLine ref : referenceLines) {
            if (ref.value() < min) min = ref.value();
            if (ref.value() > max) max = ref.value();
        }

        // Add a small padding when everything coincides on a single y value
        // (e.g. perfectly flat data and a reference line at the same value),
        // avoiding a division by zero below and centering the flat line.
        if (max - min < 1e-9) {
            double padding = max == 0 ? 1.0 : Math.abs(max) * 0.05;
            min -= padding;
            max += padding;
        }

        double minY = height + fontSize;
        double maxY = fontSize;

        LineElement minLine = faint(stroked(new LineElement()
                .from(0, minY)
                .to(viewBoxWidth, minY), lineColor)
                .strokeWidth(1)
                .strokeDasharray(2, 2), GRID_OPACITY, "sparkline-grid");

        LineElement maxLine = faint(stroked(new LineElement()
                .from(0, maxY)
                .to(viewBoxWidth, maxY), lineColor)
                .strokeWidth(1)
                .strokeDasharray(2, 2), GRID_OPACITY, "sparkline-grid");

        getElement().appendChild(minLine);
        getElement().appendChild(maxLine);

        // Draw additional series first (so primary is on top)
        final double finalMin = min;
        final double finalMax = max;
        DataSeries secondScale = null;
        double[] secondRange = null;
        for (int i = 0; i < additionalSeries.size(); i++) {
            DataSeries series = additionalSeries.get(i);
            if (series.ownScale()) {
                double[] range = range(series.data());
                appendLine(series.data(), seriesGaps.get(i), series.color(), range[0], range[1], "sparkline-series");
                if (secondScale == null) {
                    secondScale = series;
                    secondRange = range;
                }
            } else {
                appendLine(series.data(), seriesGaps.get(i), series.color(), finalMin, finalMax, "sparkline-series");
            }
        }

        // Draw primary series
        appendLine(dataPoints, primaryGaps, lineColor, min, max, "sparkline-line");

        // Draw reference lines on top of the data, with a faint default color
        for (ReferenceLine ref : referenceLines) {
            double y = height - (ref.value() - finalMin) / (finalMax - finalMin) * height + fontSize;
            LineElement refLine = stroked(new LineElement()
                    .from(0, round(y))
                    .to(viewBoxWidth, round(y)), ref.color() != null ? ref.color() : lineColor)
                    .strokeWidth(1)
                    .strokeDasharray(4, 2);
            // Only the default colour is faint; an explicit one is the caller's choice
            if (ref.color() == null) {
                faint(refLine, REFERENCE_LINE_OPACITY, null);
            }
            refLine.getClassList().add("sparkline-reference");
            getElement().appendChild(refLine);

            if (ref.label() != null) {
                // Keep the label fully opaque (only the line itself is faint)
                Color labelColor = ref.color() != null ? ref.color() : lineColor;
                TextElement refLabel = filled(new TextElement(viewBoxWidth, round(y - 2), ref.label())
                        .fontSize(fontSize)
                        .textAnchor(TextElement.TextAnchor.END), labelColor);
                refLabel.getClassList().add("sparkline-reference");
                getElement().appendChild(refLabel);
            }
        }

        // Labels
        // Locale.ROOT: the JVM's default locale is the server's, not the viewer's
        appendScaleLabels(0, TextElement.TextAnchor.START, min, max, unit, lineColor, "sparkline-line-label");
        if (secondScale != null) {
            appendScaleLabels(viewBoxWidth, TextElement.TextAnchor.END, secondRange[0], secondRange[1],
                    secondScale.unit(), secondScale.color(), "sparkline-series-label");
        }

        if (title != null) {
            // With a second scale at the right, the title moves to the middle
            boolean centred = secondScale != null;
            TextElement titleLabel = filled(new TextElement(centred ? viewBoxWidth / 2.0 : viewBoxWidth, fontSize - 2.5, title)
                    .fontSize(fontSize)
                    .fontWeight(TextElement.FontWeight.BOLD)
                    .textAnchor(centred ? TextElement.TextAnchor.MIDDLE : TextElement.TextAnchor.END), lineColor);
            titleLabel.getClassList().add("sparkline-title");
            getElement().appendChild(titleLabel);
        }

        if (timeScaleStart != null) {
            TextElement startLabel = axisLabel(filled(new TextElement(0, height + 3 * fontSize, timeScaleStart)
                    .fontSize(fontSize)
                    .textAnchor(TextElement.TextAnchor.START), lineColor));
            getElement().appendChild(startLabel);
        }
        if (timeScaleEnd != null) {
            TextElement endLabel = axisLabel(filled(new TextElement(viewBoxWidth, height + 3 * fontSize, timeScaleEnd)
                    .fontSize(fontSize)
                    .textAnchor(TextElement.TextAnchor.END), lineColor));
            getElement().appendChild(endLabel);
        }

        if (crosshairEnabled) {
            crosshairLine = faint(stroked(new LineElement()
                    .from(0, fontSize)
                    .to(0, height + fontSize), lineColor)
                    .strokeWidth(1), REFERENCE_LINE_OPACITY, "sparkline-crosshair");
            crosshairLine.setAttribute("visibility", "hidden");
            getElement().appendChild(crosshairLine);
        }

        getElement().executeJs("if(this._updateTextScale) requestAnimationFrame(this._updateTextScale)");

        // Clear data after drawing - SVG elements are already created,
        // data is no longer needed and would only consume session memory.
        // Only clear if attached (otherwise onAttach will call draw() again).
        if (getElement().getNode().isAttached()) {
            dataPoints = new ArrayList<>();
            additionalSeries = new ArrayList<>();
            dataDropped = true;
        }
    }

    /**
     * The min and max of a scale: the max above the top grid line, the min below
     * the bottom one, on a row of its own above the times. Both stay out of the
     * plot, where a curve at its extreme would run right through them.
     */
    private void appendScaleLabels(double x, TextElement.TextAnchor anchor, double min, double max,
                                   String unit, Color color, String className) {
        String suffix = unit != null ? unit : "";
        double[][] positions = {{height + 2 * fontSize - 2, min}, {fontSize - 2, max}};
        for (double[] position : positions) {
            // Locale.ROOT: the JVM's default locale is the server's, not the viewer's
            TextElement label = axisLabel(filled(new TextElement(x, position[0],
                    String.format(Locale.ROOT, "%.1f", position[1]) + suffix)
                    .fontSize(fontSize)
                    .fontWeight(TextElement.FontWeight.BOLD)
                    .textAnchor(anchor), color));
            label.getClassList().add(className);
            getElement().appendChild(label);
        }
    }

    /** The gaps of normalized data, as {from, to} ranges of its 0–1 x. */
    private static List<double[]> gapsIn(List<DataPoint> data) {
        double[] xs = new double[data.size()];
        for (int i = 0; i < xs.length; i++) {
            xs[i] = data.get(i).x();
        }
        return SparkLineGeometry.gaps(xs);
    }

    /** Min and max of a series, padded when it is flat so that it can be scaled. */
    private static double[] range(List<DataPoint> data) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (DataPoint dp : data) {
            min = Math.min(min, dp.y());
            max = Math.max(max, dp.y());
        }
        if (data.isEmpty()) {
            return new double[]{0, 1};
        }
        if (max - min < 1e-9) {
            double padding = max == 0 ? 1.0 : Math.abs(max) * 0.05;
            min -= padding;
            max += padding;
        }
        return new double[]{min, max};
    }

    /**
     * Draws a series as one path through its points, a monotone curve or
     * straight segments, broken where the data has a gap. Gaps are bridged with a
     * faint dashed line: the curve does not pretend to know what happened there,
     * but the eye can still follow the series.
     */
    private void appendLine(List<DataPoint> seriesData, List<double[]> gaps, Color color, double min, double max,
                            String className) {
        List<double[]> points = new ArrayList<>(seriesData.size());
        for (DataPoint dp : seriesData) {
            double x = dp.x() * viewBoxWidth;
            double y = height - (dp.y() - min) / (max - min) * height + fontSize;
            points.add(new double[]{x, y});
        }
        List<double[]> screenGaps = gaps.stream()
                .map(g -> new double[]{g[0] * viewBoxWidth, g[1] * viewBoxWidth}).toList();
        List<List<double[]>> runs = SparkLineGeometry.splitAtGaps(points, screenGaps);
        if (runs.isEmpty()) {
            return;
        }

        PathElement line = dataStroke(stroked(new PathElement().noFill(), color));
        for (List<double[]> run : runs) {
            double[] first = run.getFirst();
            line.moveTo(round(first[0]), round(first[1]));
            if (run.size() == 1) {
                // A lone reading between gaps: a dot, as a zero-length segment with round caps
                line.lineTo(round(first[0]) + 0.1, round(first[1]));
                line.setAttribute("stroke-linecap", "round");
            } else if (useBezierCurve) {
                for (double[] c : SparkLineGeometry.monotoneCurve(run)) {
                    line.cubicBezierTo(round(c[0]), round(c[1]), round(c[2]), round(c[3]), round(c[4]), round(c[5]));
                }
            } else {
                for (int i = 1; i < run.size(); i++) {
                    line.lineTo(round(run.get(i)[0]), round(run.get(i)[1]));
                }
            }
        }
        line.getClassList().add(className);
        getElement().appendChild(line);

        if (runs.size() > 1) {
            PathElement bridges = faint(stroked(new PathElement().noFill(), color)
                    .strokeWidth(1)
                    .strokeDasharray(3, 3), GAP_OPACITY, "sparkline-gap");
            for (int i = 1; i < runs.size(); i++) {
                double[] from = runs.get(i - 1).getLast();
                double[] to = runs.get(i).getFirst();
                bridges.moveTo(round(from[0]), round(from[1]));
                bridges.lineTo(round(to[0]), round(to[1]));
            }
            getElement().appendChild(bridges);
        }
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    /**
     * Strokes in the given colour, or in currentColor without one. Every stroke
     * keeps its width however the chart is stretched: the viewBox is scaled
     * without keeping its aspect ratio, which would otherwise draw steep segments
     * thicker than flat ones.
     */
    private static <T extends SvgGraphicsElement> T stroked(T element, Color color) {
        if (color != null) {
            element.stroke(color);
        } else {
            element.stroke("currentColor");
        }
        element.setAttribute("vector-effect", "non-scaling-stroke");
        return element;
    }

    private static <T extends SvgGraphicsElement> T dataStroke(T element) {
        return element.strokeWidth(DATA_STROKE_WIDTH);
    }

    private static <T extends SvgGraphicsElement> T filled(T element, Color color) {
        if (color != null) {
            element.fill(color);
        } else {
            element.fill("currentColor");
        }
        return element;
    }

    private static <T extends SvgGraphicsElement> T faint(T element, double opacity, String className) {
        element.setAttribute("stroke-opacity", String.valueOf(opacity));
        if (className != null) {
            element.getClassList().add(className);
        }
        return element;
    }

    private static TextElement axisLabel(TextElement label) {
        label.setAttribute("fill-opacity", String.valueOf(AXIS_LABEL_OPACITY));
        label.getClassList().add("sparkline-label");
        return label;
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
