package org.vaadin.svgvis.unit;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.dom.DomEvent;
import com.vaadin.flow.internal.JacksonUtils;
import com.vaadin.flow.internal.nodefeature.ElementListenerMap;
import org.junit.jupiter.api.Test;
import org.vaadin.svgvis.SvgSparkLine;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The crosshair's contract with the page around it: the chart leaves vertical
 * scrolling to the browser, a pick reaches the listener, and attaching the
 * chart again does not make it report twice.
 */
public class SvgSparkLineCrosshairTest extends BrowserlessTest {

    private final List<Double> reported = new ArrayList<>();

    private SvgSparkLine chart() {
        SvgSparkLine chart = new SvgSparkLine(100);
        chart.setData(1, 3, 2);
        chart.setCrosshairListener(reported::add);
        UI.getCurrent().add(chart);
        return chart;
    }

    private static void pick(SvgSparkLine chart, double relPos) {
        var data = JacksonUtils.createObjectNode();
        data.put("event.detail", relPos);
        chart.getElement().getNode().getFeature(ElementListenerMap.class)
                .fireEvent(new DomEvent(chart.getElement(), "svgvis-crosshair-pick", data));
    }

    /*
       A finger that lands on the chart to scroll the page must scroll the page.
       The chart claims horizontal movement only.
    */
    @Test
    public void verticalPanningIsLeftToTheBrowser() {
        assertEquals("pan-y", chart().getElement().getStyle().get("touch-action"));
    }

    @Test
    public void aPickReachesTheListener() {
        SvgSparkLine chart = chart();

        pick(chart, 0.25);

        assertEquals(List.of(0.25), reported);
    }

    @Test
    public void attachingAgainDoesNotReportTwice() {
        SvgSparkLine chart = chart();
        UI.getCurrent().remove(chart);
        UI.getCurrent().add(chart);

        pick(chart, 0.5);

        assertEquals(List.of(0.5), reported);
    }
}
