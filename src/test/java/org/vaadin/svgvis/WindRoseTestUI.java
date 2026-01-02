package org.vaadin.svgvis;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.router.Route;
import in.virit.color.NamedColor;
import in.virit.color.RgbColor;

@Route
public class WindRoseTestUI extends VerticalLayout {

    private Paragraph clickInfo = new Paragraph("Click a sector to see details");

    public WindRoseTestUI() {
        add(new H3("WindRose Component Test"));

        HorizontalLayout examples = new HorizontalLayout();
        examples.setWidthFull();
        examples.getStyle().setFlexWrap(Style.FlexWrap.WRAP);

        examples.add(createBasicWindRose());
        examples.add(createMultiSeriesWindRose());
        examples.add(createInteractiveWindRose());
        examples.add(createCustomSectorsWindRose());

        add(examples);
        add(clickInfo);
        clickInfo.setId("click-info");
    }

    private Div createBasicWindRose() {
        Div container = new Div();
        container.add(new Paragraph("Basic (16 sectors)"));

        WindRose windRose = new WindRose(200);
        windRose.setTitle("Wind Distribution");

        double[] data = generateSampleWindData(16);
        windRose.addSeries("Duration", NamedColor.BLUE, data);
        windRose.draw();

        windRose.setId("basic-windrose");
        container.add(windRose);
        return container;
    }

    private Div createMultiSeriesWindRose() {
        Div container = new Div();
        container.add(new Paragraph("Multi-Series"));

        WindRose windRose = new WindRose(200) {{
            setTitle("Duration vs Energy");
            addSeries("Duration", new RgbColor(100, 149, 237), generateSampleWindData(16));
            addSeries("Energy", new RgbColor(255, 140, 0), generateSampleEnergyData(16));
            draw();
        }};

        windRose.setId("multi-series-windrose");
        container.add(windRose);
        return container;
    }

    private Div createInteractiveWindRose() {
        Div container = new Div();
        container.add(new Paragraph("Interactive (click sectors)"));

        WindRose windRose = new WindRose(200) {{
            setTitle("Click to Select");
            addSeries("Data", NamedColor.GREEN, generateSampleWindData(16));
            setSectorClickListener(data -> {
                clickInfo.setText(String.format(
                        "Sector %d (%s, %d°): Value=%.1f (%.1f%%)",
                        data.sectorIndex(),
                        data.directionLabel(),
                        data.centerDegrees(),
                        data.seriesValues().get(0),
                        data.seriesPercentages().get(0)
                ));
            });
            draw();
        }};

        windRose.setId("interactive-windrose");
        container.add(windRose);
        return container;
    }

    private Div createCustomSectorsWindRose() {
        Div container = new Div();
        container.add(new Paragraph("8 Sectors (no sector lines)"));

        WindRose windRose = new WindRose(200, 8) {{
            setTitle("8 Sectors");
            setShowSectorLines(false);
            addSeries("Wind", NamedColor.PURPLE, generateSampleWindData(8));
            draw();
        }};

        windRose.setId("custom-sectors-windrose");
        container.add(windRose);
        return container;
    }

    private double[] generateSampleWindData(int sectors) {
        double[] data = new double[sectors];
        double degreesPerSector = 360.0 / sectors;

        for (int i = 0; i < sectors; i++) {
            double angle = i * degreesPerSector;
            // Simulate more wind from SW (around 225°)
            double swDistance = Math.abs(angle - 225);
            if (swDistance > 180) swDistance = 360 - swDistance;
            data[i] = Math.max(5, 100 - swDistance * 0.4 + Math.random() * 20);
        }
        return data;
    }

    private double[] generateSampleEnergyData(int sectors) {
        double[] data = new double[sectors];
        double degreesPerSector = 360.0 / sectors;

        for (int i = 0; i < sectors; i++) {
            double angle = i * degreesPerSector;
            // Energy peaks from W (around 270°)
            double wDistance = Math.abs(angle - 270);
            if (wDistance > 180) wDistance = 360 - wDistance;
            data[i] = Math.max(10, 150 - wDistance * 0.5 + Math.random() * 30);
        }
        return data;
    }
}
