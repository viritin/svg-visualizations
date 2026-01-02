package org.vaadin.svgvis.it;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import in.virit.mopo.Mopo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SvgVisualizationsIT {

    @Value("${local.server.port}")
    private int port;

    static Playwright playwright = Playwright.create();

    private Browser browser;
    private Page page;
    private Mopo mopo;

    @BeforeEach
    public void setup() {
        browser = playwright.chromium()
                .launch(new BrowserType.LaunchOptions()
                        .setHeadless(false)
                        .setDevtools(true)
                );

        page = browser.newPage();
        page.setDefaultTimeout(5000);
        mopo = new Mopo(page);
    }

    @AfterEach
    public void closePlaywright() {
        page.close();
        browser.close();
    }

    @Test
    public void smokeTestAllTestUIs() {
        String rootUrl = "http://localhost:" + port + "/";
        mopo.getViewsReportedByDevMode(browser, rootUrl).forEach(viewName -> {
            String url = rootUrl + viewName;
            page.navigate(url);
            mopo.assertNoJsErrors();
            System.out.println("Checked %s and it contained no JS errors.".formatted(viewName));
        });
    }

    @Test
    public void testWindRoseRendering() {
        String url = "http://localhost:" + port + "/windrosetestui";
        page.navigate(url);
        mopo.assertNoJsErrors();

        // Check that all WindRose components are rendered
        Locator basicWindRose = page.locator("#basic-windrose");
        basicWindRose.waitFor();
        assertThat(basicWindRose).isVisible();

        Locator multiSeriesWindRose = page.locator("#multi-series-windrose");
        assertThat(multiSeriesWindRose).isVisible();

        Locator interactiveWindRose = page.locator("#interactive-windrose");
        assertThat(interactiveWindRose).isVisible();

        Locator customSectorsWindRose = page.locator("#custom-sectors-windrose");
        assertThat(customSectorsWindRose).isVisible();

        // Verify SVG content is present
        Locator svgElements = basicWindRose.locator("circle, path, text");
        assertThat(svgElements.first()).isVisible();
    }

    @Test
    public void testWindRoseSectorClick() {
        String url = "http://localhost:" + port + "/windrosetestui";
        page.navigate(url);
        mopo.assertNoJsErrors();

        Locator interactiveWindRose = page.locator("#interactive-windrose");
        interactiveWindRose.waitFor();

        // Click on the WindRose (a sector)
        interactiveWindRose.click();

        // Check that click info is updated
        Locator clickInfo = page.locator("#click-info");
        // After click, the text should contain sector information
        page.waitForCondition(() -> !clickInfo.textContent().contains("Click a sector"));
        assertThat(clickInfo).containsText("Sector");
    }

    @Test
    public void testSvgSparkLineRendering() {
        String url = "http://localhost:" + port + "/svgsparklinetestui";
        page.navigate(url);
        mopo.assertNoJsErrors();

        // Check that all SparkLine components are rendered
        Locator basicSparkLine = page.locator("#basic-sparkline");
        basicSparkLine.waitFor();
        assertThat(basicSparkLine).isVisible();

        Locator coloredSparkLine = page.locator("#colored-sparkline");
        assertThat(coloredSparkLine).isVisible();

        Locator multiSeriesSparkLine = page.locator("#multi-series-sparkline");
        assertThat(multiSeriesSparkLine).isVisible();

        Locator interactiveSparkLine = page.locator("#interactive-sparkline");
        assertThat(interactiveSparkLine).isVisible();

        Locator noSmoothingSparkLine = page.locator("#no-smoothing-sparkline");
        assertThat(noSmoothingSparkLine).isVisible();

        Locator fluidSparkLine = page.locator("#fluid-sparkline");
        assertThat(fluidSparkLine).isVisible();

        // Verify SVG path content is present
        Locator svgPaths = basicSparkLine.locator("path, polyline, line");
        assertThat(svgPaths.first()).isVisible();
    }

    @Test
    public void testSvgSparkLineCrosshair() {
        String url = "http://localhost:" + port + "/svgsparklinetestui";
        page.navigate(url);
        mopo.assertNoJsErrors();

        Locator interactiveSparkLine = page.locator("#interactive-sparkline");
        interactiveSparkLine.waitFor();

        // Hover over the sparkline to trigger crosshair
        interactiveSparkLine.hover();

        // Check that crosshair info is updated
        Locator crosshairInfo = page.locator("#crosshair-info");
        page.waitForCondition(() -> !crosshairInfo.textContent().contains("Move mouse over"));
        assertThat(crosshairInfo).containsText("Position:");
    }

    @Test
    public void testWindRoseSvgStructure() {
        String url = "http://localhost:" + port + "/windrosetestui";
        page.navigate(url);
        mopo.assertNoJsErrors();

        Locator basicWindRose = page.locator("#basic-windrose");
        basicWindRose.waitFor();

        // Check for reference circles (4 grid circles)
        Locator circles = basicWindRose.locator("circle");
        assertThat(circles.first()).isVisible();

        // Check for cardinal direction labels
        Locator textElements = basicWindRose.locator("text");
        assertThat(textElements).not().hasCount(0);

        // Check for sector wedges (paths)
        Locator paths = basicWindRose.locator("path");
        assertThat(paths).not().hasCount(0);
    }

    @Test
    public void testSvgSparkLineSvgStructure() {
        String url = "http://localhost:" + port + "/svgsparklinetestui";
        page.navigate(url);
        mopo.assertNoJsErrors();

        Locator noSmoothingSparkLine = page.locator("#no-smoothing-sparkline");
        noSmoothingSparkLine.waitFor();

        // Check for polyline (no smoothing mode uses polyline)
        Locator polylines = noSmoothingSparkLine.locator("polyline");
        assertThat(polylines.first()).isVisible();

        // Check for dashed reference lines
        Locator lines = noSmoothingSparkLine.locator("line");
        assertThat(lines).not().hasCount(0);

        // Check for text labels (min/max values, title)
        Locator textElements = noSmoothingSparkLine.locator("text");
        assertThat(textElements).not().hasCount(0);
    }

    @Test
    public void testRealWeatherData() {
        String url = "http://localhost:" + port + "/realweatherdatatestui";
        page.navigate(url);

        // Wait for data to load (may take a few seconds for 950k records)
        page.setDefaultTimeout(30000);

        // Click Load Data button
        Locator loadButton = page.locator("text=Load Data");
        loadButton.click();

        // Wait for visualization buttons to appear
        Locator tempButton = page.locator("vaadin-button:has-text('Temperature')");
        tempButton.waitFor();

        // Click Temperature button and verify sparkline renders
        tempButton.click();
        Locator tempSparkline = page.locator("#temp-sparkline");
        tempSparkline.waitFor();
        assertThat(tempSparkline).isVisible();

        // Verify the button shows timing info after click
        page.waitForCondition(() -> tempButton.textContent().contains("ms"));
        assertThat(tempButton).containsText("server:");

        // Click Wind Rose button
        Locator windRoseButton = page.locator("vaadin-button:has-text('Wind Rose')");
        windRoseButton.click();
        Locator windRose = page.locator("#wind-rose");
        windRose.waitFor();
        assertThat(windRose).isVisible();

        // Click Pressure button
        Locator pressureButton = page.locator("vaadin-button:has-text('Pressure')");
        pressureButton.click();
        Locator pressureSparkline = page.locator("#pressure-sparkline");
        pressureSparkline.waitFor();
        assertThat(pressureSparkline).isVisible();

        // Click Solar Radiation button
        Locator solarButton = page.locator("vaadin-button:has-text('Solar')");
        solarButton.click();
        Locator solarSparkline = page.locator("#solar-sparkline");
        solarSparkline.waitFor();
        assertThat(solarSparkline).isVisible();

        mopo.assertNoJsErrors();
    }
}
