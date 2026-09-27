package org.vaadin.svgvis;

import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import in.virit.color.NamedColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Four quantities in one chart, each against its own scale: the primary at the
 * left, the other three side by side at the right.
 */
@Route
public class MultiScaleTestUI extends VerticalLayout {

    public MultiScaleTestUI() {
        setMaxWidth("40rem");
        add(new H3("Several scales"));
        add(new Paragraph("Temperature, pressure, battery voltage and signal strength."));
        var chart = new SvgSparkLine(100);
        chart.setUnit(" °C");
        chart.setData(wave(22, 1.5, 0));
        chart.addSeriesWithOwnScale(wave(1010, 2, 1), NamedColor.DARKORANGE, " hPa");
        chart.addSeriesWithOwnScale(wave(3.0, 0.1, 2), NamedColor.SEAGREEN, " V");
        chart.addSeriesWithOwnScale(wave(-55, 8, 3), NamedColor.MEDIUMVIOLETRED, " dBm");
        add(chart);
    }

    private static List<SvgSparkLine.DataPoint> wave(double base, double amplitude, double phase) {
        List<SvgSparkLine.DataPoint> points = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            points.add(SvgSparkLine.DataPoint.of(i, base + amplitude * Math.sin(i / 30.0 + phase)));
        }
        return points;
    }
}
