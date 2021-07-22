package com.github.donvip;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;
import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.io.IOUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.AbstractXYItemRenderer;
import org.jfree.chart.renderer.xy.SamplingXYLineRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYDataset;
import org.jfree.svg.SVGGraphics2D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.github.donvip.GpHistoryQuery.GpHistory;
import com.github.donvip.GpHistoryQuery.GpHistoryQueryField;
import com.stevenpaligo.spacetrack.client.SatCatQuery;
import com.stevenpaligo.spacetrack.client.SatCatQuery.SatCat;
import com.stevenpaligo.spacetrack.client.SatCatQuery.SatCatQueryField;
import com.stevenpaligo.spacetrack.client.credential.CredentialProvider;
import com.stevenpaligo.spacetrack.client.credential.DefaultCredentialProvider;
import com.stevenpaligo.spacetrack.client.predicate.Equal;

@Service
public class SatDecayGraphService {

    private static final Logger logger = LoggerFactory.getLogger(SatDecayGraphService.class);

    @Value("${spaceTrackLogin}")
    private String spaceTrackLogin;

    @Value("${spaceTrackPassword}")
    private String spaceTrackPassword;

    @Value("${satIds}")
    private List<Integer> satIds;

    @Value("${satIntlDes}")
    private List<String> satIntlDes;

    @Value("${width:1920}")
    private int width;

    @Value("${height:1080}")
    private int height;

    @Value("${strokeWidth:4.0}")
    private float strokeWidth;

    @Value("${shapeSize:10.0}")
    private double shapeSize;

    private static String generateSVGForChart(JFreeChart chart, int width, int height) {
        SVGGraphics2D g2 = new SVGGraphics2D(width, height);
        chart.setElementHinting(true);
        chart.draw(g2, new Rectangle(width, height));
        return g2.getSVGElement(chart.getID());
    }

    private static XYDataset createDataset(List<GpHistory> history) {
        final TimeSeries apoapsis = new TimeSeries("Apoapsis");
        final TimeSeries periapsis = new TimeSeries("Periapsis");

        for (GpHistory gp : history) {
            RegularTimePeriod date = new Millisecond(Date.from(gp.getEpoch().toInstant()));
            apoapsis.addOrUpdate(date, gp.getApoapsis());
            periapsis.addOrUpdate(date, gp.getPeriapsis());
        }

        TimeSeriesCollection result = new TimeSeriesCollection();
        result.addSeries(apoapsis);
        result.addSeries(periapsis);
        return result;
    }

    private JFreeChart createChart(XYDataset dataset, String title) {
        // Time axis, UTC / English
        ValueAxis timeAxis = new DateAxis("Time (UTC)", TimeZone.getTimeZone("UTC"), Locale.ENGLISH);
        timeAxis.setLowerMargin(0.02);
        timeAxis.setUpperMargin(0.02);

        // Value axis (same on both sides for readability)
        NumberAxis leftAxis = new NumberAxis("Kilometers");
        leftAxis.setAutoRangeIncludesZero(false);
        NumberAxis rightAxis = new NumberAxis(null);
        rightAxis.setAutoRangeIncludesZero(false);

        // Create plot
        double delta = shapeSize / 2.0;
        boolean small = dataset.getItemCount(0) < 500;
        AbstractXYItemRenderer renderer = small ? new XYLineAndShapeRenderer(true, true)
                : new SatSamplingXYLineRenderer();
        renderer.setDefaultStroke(new BasicStroke(strokeWidth));
        renderer.setDefaultShape(new Ellipse2D.Double(-delta, -delta, shapeSize, shapeSize));
        renderer.setAutoPopulateSeriesStroke(false);
        renderer.setAutoPopulateSeriesShape(false);
        XYPlot plot = new XYPlot(dataset, timeAxis, leftAxis, renderer);
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeAxis(1, rightAxis);
        plot.setRangeAxisLocation(1, AxisLocation.TOP_OR_RIGHT);

        // Ensure both axes have the same range
        rightAxis.setRange(leftAxis.getRange(), false, false);

        // Create and return chart
        JFreeChart chart = new JFreeChart(title, JFreeChart.DEFAULT_TITLE_FONT, plot, true);
        chart.setBackgroundPaint(Color.WHITE);
        return chart;
    }

    private static class SatSamplingXYLineRenderer extends SamplingXYLineRenderer {
        private static final long serialVersionUID = 1L;

        SatSamplingXYLineRenderer() {
            setTreatLegendShapeAsLine(false);
        }
    }

    private static void apiThrottle() throws InterruptedException {
        // API throttle: Limit API queries to less than 30 requests per minute / 300 requests per hour
        Thread.sleep(2500);
    }

    private void doGenerateGraphs(List<Integer> ids, CredentialProvider credentials, Map<String, String[]> map)
            throws IOException, InterruptedException {
        for (Integer id : ids) {
            logger.info("Fetching history for satellite {}", id);
            List<GpHistory> history = new GpHistoryQuery().setCredentials(credentials)
                            .addPredicate(new Equal<>(GpHistoryQueryField.CATALOG_NUMBER, id)).execute()
                            .stream().sorted(Comparator.comparing(GpHistory::getEpoch)).collect(Collectors.toList());
            if (!history.isEmpty()) {
                String objectName = history.get(0).getObjectName();
                String idAsString = id.toString();
                Optional<String[]> row = map.values().stream().filter(t -> t[2].equals(idAsString)).findFirst();
                // Celestrak has better names than space-track
                if (row.isPresent()) {
                    objectName = row.get()[0];
                }
                logger.info("Generating graph for satellite {} - {}", id, objectName);
                String filename = objectName.replace('/', '-').replace('\\', '-') + " decay.svg";
                Files.writeString(Path.of(filename), generateSVGForChart(
                        createChart(createDataset(history), objectName + " altitude"), width, height));
                logger.info("Graph generated for satellite {}: {}", id, filename);
            } else {
                logger.error("Unable to generate graph for satellite {}", id);
            }
            apiThrottle();
        }
    }

    private Map<String, String[]> getCelestrakMapping() throws IOException {
        logger.info("Retrieving SATCAT data from CelesTrak...");
        HttpsURLConnection connection = (HttpsURLConnection) new URL("https://celestrak.com/pub/satcat.csv").openConnection();
        try {
            return IOUtils.toString(connection.getInputStream(), StandardCharsets.UTF_8).lines()
                    .map(l -> l.split(","))
                    .filter(t -> t.length >= 3)
                    .collect(Collectors.toMap(t -> t[1], t -> t));
        } finally {
            connection.disconnect();
        }
    }

    private List<Integer> getSatIdsFromSatIntDes(Map<String, String[]> map, CredentialProvider credentials) {
        return satIntlDes.stream().flatMap(d -> {
            try {
                Integer catalogNumber = null;
                final String id = map.get(d)[2];
                if (id != null && !id.isBlank()) {
                    catalogNumber = Integer.valueOf(id);
                } else {
                    List<SatCat> satcat = new SatCatQuery().setCredentials(credentials)
                            .addPredicate(new Equal<>(SatCatQueryField.INTERNATIONAL_DESIGNATOR, d.trim())).execute();
                    if (!satcat.isEmpty()) {
                        catalogNumber = satcat.get(0).getCatalogNumber();
                    }
                    apiThrottle();
                }
                if (catalogNumber != null) {
                    logger.info("Mapped satellite international designator {} to catalog number {}", d, catalogNumber);
                    return Stream.of(catalogNumber);
                }
                return Stream.empty();
            } catch (IOException | InterruptedException | NumberFormatException e) {
                logger.error("Failed to retrieve satcat " + d, e);
                return Stream.empty();
            }
        }).filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    @PostConstruct
    public void generateGraphs() throws IOException, InterruptedException {
        CredentialProvider credentials = new DefaultCredentialProvider(spaceTrackLogin, spaceTrackPassword);
        // SpaceTrack API has a very restrictive API Throttling, so download a mapping from CelesTrak first
        Map<String, String[]> map = getCelestrakMapping();
        doGenerateGraphs(satIds, credentials, map);
        if (!satIntlDes.isEmpty()) {
            doGenerateGraphs(getSatIdsFromSatIntDes(map, credentials), credentials, map);
        }
    }
}
