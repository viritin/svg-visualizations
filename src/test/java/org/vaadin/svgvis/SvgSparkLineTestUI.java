package org.vaadin.svgvis;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.internal.StateTree;
import com.vaadin.flow.internal.StateTree.BeforeClientResponseEntry;
import com.vaadin.flow.router.Route;
import in.virit.color.NamedColor;
import in.virit.color.RgbColor;
import org.vaadin.firitin.components.button.VButton;

import java.time.Instant;

@Route
public class SvgSparkLineTestUI extends VerticalLayout {

    private Paragraph crosshairInfo = new Paragraph("Move mouse over interactive chart");

    public SvgSparkLineTestUI() {
        setWidthFull();
        add(new H3("SvgSparkLine Component Test"));

        add(createBasicSparkLine());
        add(createColoredSparkLine());
        add(createMultiSeriesSparkLine());
        add(createInteractiveSparkLine());
        add(createNoSmoothingSparkLine());
        add(createFluidWidthSparkLine());

        add(crosshairInfo);
        crosshairInfo.setId("crosshair-info");
    }

    private Div createBasicSparkLine() {
        Div container = new Div();
        container.add(new Paragraph("Basic Sparkline (300x60)"));

        SvgSparkLine sparkLine = new SvgSparkLine(300, 60);
        sparkLine.setData(generateSineWave(50));
        sparkLine.setTitle("Temperature");
        sparkLine.setId("basic-sparkline");
        container.add(sparkLine);
        return container;
    }

    private Div createColoredSparkLine() {
        Div container = new Div();
        container.add(new Paragraph("Colored with Time Scale"));

        SvgSparkLine sparkLine = new SvgSparkLine(400, 80) {{
            setLineColor(new RgbColor(220, 20, 60)); // Crimson
            setData(generateRandomWalk(100));
            setTitle("Stock Price");
            setTimeScale("09:00", "17:00");
        }};

        sparkLine.setId("colored-sparkline");
        container.add(sparkLine);
        return container;
    }

    private Div createMultiSeriesSparkLine() {
        Div container = new Div();
        container.add(new Paragraph("Multi-Series"));

        SvgSparkLine sparkLine = new SvgSparkLine(400, 100) {{
            setLineColor(NamedColor.BLUE);
            setData(generateSineWave(80));
            addSeries(generateCosineWave(80), NamedColor.RED);
            addSeries(generateLinear(80), NamedColor.GREEN);
            setTitle("Multiple Series");
        }};

        sparkLine.setId("multi-series-sparkline");
        container.add(sparkLine);
        return container;
    }

    private Div createInteractiveSparkLine() {
        Div container = new Div();
        container.add(new Paragraph("Interactive with Crosshair"));

        double[] data = generateSineWave(200);
        SvgSparkLine sparkLine = new SvgSparkLine(500, 100) {{
            setLineColor(NamedColor.DARKORANGE);
            setData(data);
            setTitle("Hover/Click to See Values");
            setCrosshairListener(relPos -> {
                // Use relative position to find index in original data
                int index = (int) (relPos * (data.length - 1));
                index = Math.max(0, Math.min(index, data.length - 1));
                double value = data[index];
                crosshairInfo.setText(String.format(
                        "Position: %.1f%%, Index: %d, Value: %.2f",
                        relPos * 100, index, value
                ));
            });
        }};

        sparkLine.setId("interactive-sparkline");
        container.add(sparkLine);
        return container;
    }

    private Div createNoSmoothingSparkLine() {
        Div container = new Div();
        container.add(new Paragraph("No Smoothing, Polyline"));

        SvgSparkLine sparkLine = new SvgSparkLine(300, 60) {{
            setSmoothing(SvgSparkLine.Smoothing.NONE);
            setUseBezierCurve(false);
            setLineColor(NamedColor.PURPLE);
            setData(5, 15, 10, 25, 20, 30, 15, 35, 25, 40);
            setTitle("Raw Data");
        }};

        sparkLine.setId("no-smoothing-sparkline");
        container.add(sparkLine);
        return container;
    }

    private Div createFluidWidthSparkLine() {
        Div container = new Div();
        container.setWidthFull();
        container.add(new Paragraph("Fluid Width (100%)"));

        SvgSparkLine sparkLine = new SvgSparkLine(80) {{
            setLineColor(NamedColor.TEAL);
            setData(generateRandomWalk(150));
            setTitle("Full Width Chart");
            setTimeScale("Jan", "Dec");
        }};

        sparkLine.setId("fluid-sparkline");
        container.add(sparkLine);
        return container;
    }

    private double[] generateSineWave(int points) {
        double[] data = new double[points];
        for (int i = 0; i < points; i++) {
            data[i] = Math.sin(i * 0.15) * 40 + 50;
        }
        return data;
    }

    private double[] generateCosineWave(int points) {
        double[] data = new double[points];
        for (int i = 0; i < points; i++) {
            data[i] = Math.cos(i * 0.15) * 35 + 50;
        }
        return data;
    }

    private double[] generateLinear(int points) {
        double[] data = new double[points];
        for (int i = 0; i < points; i++) {
            data[i] = (double) i / points * 60 + 20;
        }
        return data;
    }

    private double[] generateRandomWalk(int points) {
        double[] data = new double[points];
        data[0] = 50;
        for (int i = 1; i < points; i++) {
            data[i] = data[i - 1] + (Math.random() - 0.5) * 10;
            data[i] = Math.max(0, Math.min(100, data[i]));
        }
        return data;
    }

}
