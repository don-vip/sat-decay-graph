package com.github.donvip;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;
import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.io.IOUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.LegendItemCollection;
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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.github.donvip.GpHistoryQuery.GpHistory;
import com.github.donvip.GpHistoryQuery.GpHistoryQueryField;
import com.stevenpaligo.spacetrack.client.SatCatQuery;
import com.stevenpaligo.spacetrack.client.SatCatQuery.SatCat;
import com.stevenpaligo.spacetrack.client.SatCatQuery.SatCatQueryField;
import com.stevenpaligo.spacetrack.client.credential.CredentialProvider;
import com.stevenpaligo.spacetrack.client.credential.DefaultCredentialProvider;
import com.stevenpaligo.spacetrack.client.predicate.Equal;
import com.stevenpaligo.spacetrack.client.predicate.GreaterThan;
import com.stevenpaligo.spacetrack.client.predicate.LessThan;

@Service
public class SatDecayGraphService {

    private static final Logger logger = LoggerFactory.getLogger(SatDecayGraphService.class);

    @Value("${spaceTrackLogin}")
    private String spaceTrackLogin;

    @Value("${spaceTrackPassword}")
    private String spaceTrackPassword;

    @Value("${plotMode:distinct}")
    private PlotMode plotMode;

    @Value("${combinedFileName:output.svg}")
    private String combinedFileName;

    @Value("${domainGridlinesVisible:false}")
    private boolean domainGridlinesVisible;

    @Value("${rangeGridlinesVisible:false}")
    private boolean rangeGridlinesVisible;

    @Value("${startDate:#{null}}")
    private Instant startDate;

    @Value("${endDate:#{null}}")
    private Instant endDate;

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
        chart.draw(g2, new Rectangle(width, height));
        return g2.getSVGElement(chart.getID());
    }

    private static void addTimeSeries(TimeSeriesCollection apoApsisCollection, TimeSeriesCollection periApsisCollection,
            List<GpHistory> history, String prefix, boolean distinguish) {
        final TimeSeries apoapsis = new TimeSeries(prefix + (distinguish ? "Apoapsis" : ""));
        final TimeSeries periapsis = new TimeSeries(prefix + (distinguish ? "Periapsis" : ""));

        for (GpHistory gp : history) {
            RegularTimePeriod date = new Millisecond(Date.from(gp.getEpoch().toInstant()));
            apoapsis.addOrUpdate(date, gp.getApoapsis());
            periapsis.addOrUpdate(date, gp.getPeriapsis());
        }

        apoApsisCollection.addSeries(apoapsis);
        periApsisCollection.addSeries(periapsis);
    }

    /**
     * Creates one or two datasets, based on the {@code distringuish} parameter
     *
     * @param histories List of GP_HISTORY records returned by Space-Track API
     * @param distinguish if {@code true}, creates a single data set where apoapsis
     *            and periapsis series have distinguished names. If {@code false},
     *            creates two datasets where apoapsis and periapsis have the same
     *            name. The use of two datasets allow to render the plot with the
     *            same colors for both series.
     * @return the created datasets
     */
    private static List<XYDataset> createDatasets(Map<String, List<GpHistory>> histories, boolean distinguish) {
        TimeSeriesCollection apoapsis = new TimeSeriesCollection();
        TimeSeriesCollection periapsis = new TimeSeriesCollection();
        histories.forEach((prefix, history) -> addTimeSeries(apoapsis, distinguish ? apoapsis : periapsis, history,
                prefix.isEmpty() ? prefix : prefix + ' ', distinguish));
        return distinguish ? List.of(apoapsis) : List.of(apoapsis, periapsis);
    }

    private JFreeChart createChart(List<XYDataset> datasets, String title) {
        // Create plot (downsampling very large data to avoid huge SVG files)
        XYPlot plot = createPlot(datasets);

        // Create and return chart
        JFreeChart chart = new JFreeChart(title, JFreeChart.DEFAULT_TITLE_FONT, plot, true);
        chart.setBackgroundPaint(Color.WHITE);
        return chart;
    }

    private XYPlot createPlot(List<XYDataset> datasets) {
        // Time axis, UTC / English
        ValueAxis timeAxis = new DateAxis("Time (UTC)", TimeZone.getTimeZone("UTC"), Locale.ENGLISH);
        timeAxis.setLowerMargin(0.02);
        timeAxis.setUpperMargin(0.02);

        // Value axis (same on both sides for readability)
        NumberAxis leftAxis = new NumberAxis("Kilometers");
        leftAxis.setAutoRangeIncludesZero(false);
        NumberAxis rightAxis = new NumberAxis(null);
        rightAxis.setAutoRangeIncludesZero(false);

        // Create plot (downsampling very large data to avoid huge SVG files)
        double delta = shapeSize / 2.0;
        boolean small = datasets.get(0).getItemCount(0) < 500;
        AbstractXYItemRenderer renderer = createRenderer(small, delta);

        // If multiple datasets, assume they have the same series and we want only to
        // show series of first dataset in legend
        XYPlot plot = new XYPlot(datasets.get(0), timeAxis, leftAxis, renderer);
        int datasetsSize = datasets.size();
        if (datasetsSize > 1) {
            LegendItemCollection legendItems = new LegendItemCollection();
            for (int i = 0; i < datasets.get(0).getSeriesCount(); i++) {
                legendItems.add(renderer.getLegendItem(0, i));
            }
            plot.setFixedLegendItems(legendItems);
            for (int i = 1; i < datasetsSize; i++) {
                plot.setDataset(i, datasets.get(i));
            }
        }
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeAxis(1, rightAxis);
        plot.setRangeAxisLocation(1, AxisLocation.TOP_OR_RIGHT);
        plot.setDomainGridlinesVisible(domainGridlinesVisible);
        plot.setRangeGridlinesVisible(rangeGridlinesVisible);

        // Ensure both axes have the same range
        rightAxis.setRange(leftAxis.getRange(), false, false);

        return plot;
    }

    private AbstractXYItemRenderer createRenderer(boolean small, double delta) {
        AbstractXYItemRenderer renderer = small ? new XYLineAndShapeRenderer(true, true)
                : new SatSamplingXYLineRenderer();
        renderer.setDefaultStroke(new BasicStroke(strokeWidth));
        renderer.setDefaultShape(new Ellipse2D.Double(-delta, -delta, shapeSize, shapeSize));
        renderer.setAutoPopulateSeriesStroke(false);
        renderer.setAutoPopulateSeriesShape(false);
        return renderer;
    }

    private static class SatSamplingXYLineRenderer extends SamplingXYLineRenderer {
        private static final long serialVersionUID = 1L;

        SatSamplingXYLineRenderer() {
            setTreatLegendShapeAsLine(false);
        }
    }

    private enum PlotMode {
        combined, distinct;
    }

    private static void apiThrottle() throws InterruptedException {
        // API throttle: Limit API queries to less than 30 requests per minute / 300 requests per hour
        Thread.sleep(2500);
    }

    private List<GpHistory> fetchHistory(Integer id, CredentialProvider credentials)
            throws JsonParseException, JsonMappingException, IOException {
        GpHistoryQuery q = new GpHistoryQuery().setCredentials(credentials)
                .addPredicate(new Equal<>(GpHistoryQueryField.CATALOG_NUMBER, id));
        if (startDate != null) {
            q.addPredicate(new GreaterThan<>(GpHistoryQueryField.EPOCH, startDate));
        }
        if (endDate != null) {
            q.addPredicate(new LessThan<>(GpHistoryQueryField.EPOCH, endDate));
        }
        return q.execute().stream().sorted(comparing(GpHistory::getEpoch)).collect(toList());
    }

    private void doGenerateGraphs(List<Integer> ids, CredentialProvider credentials, Map<String, String[]> map)
            throws IOException, InterruptedException {
        Map<String, List<GpHistory>> histories = new TreeMap<>();
        for (Integer id : ids) {
            logger.info("Fetching history for satellite {}", id);
            List<GpHistory> history = fetchHistory(id, credentials);
            if (history.isEmpty()) {
                logger.error("Unable to generate graph for satellite {} (empty history)", id);
            } else {
                String objectName = findObjectName(map, id, history);
                switch (plotMode) {
                case combined:
                    histories.put(objectName, history);
                    break;
                case distinct:
                    logger.info("Generating graph for satellite {} - {}", id, objectName);
                    String filename = objectName.replace('/', '-').replace('\\', '-') + " altitude.svg";
                    Files.writeString(Path.of(filename), generateSVGForChart(
                            createChart(createDatasets(Map.of("", history), true), objectName + " altitude"), width,
                            height));
                    logger.info("Graph generated for satellite {}: {}", id, filename);
                    break;
                default:
                    throw new UnsupportedOperationException(Objects.toString(plotMode));
                }
            }
            apiThrottle();
        }
        if (PlotMode.combined == plotMode && !histories.isEmpty()) {
            Set<String> objectNames = histories.keySet();
            logger.info("Generating graph for satellites {} - {}", ids, objectNames);
            Files.writeString(Path.of(combinedFileName),
                    generateSVGForChart(
                            createChart(createDatasets(histories, false),
                                    "Altitude of " + String.join(", ", objectNames)),
                            width, height));
            logger.info("Graph generated for satellites {}: {}", ids, combinedFileName);
        }
    }

    private static String findObjectName(Map<String, String[]> map, Integer id, List<GpHistory> history) {
        String objectName = history.get(0).getObjectName();
        String idAsString = id.toString();
        Optional<String[]> row = map.values().stream().filter(t -> t[2].equals(idAsString)).findFirst();
        // Celestrak has better names than space-track
        if (row.isPresent()) {
            objectName = row.get()[0];
        }
        return objectName;
    }

    private Map<String, String[]> getCelestrakMapping() throws IOException {
        logger.info("Retrieving SATCAT data from CelesTrak...");
        HttpsURLConnection connection = (HttpsURLConnection) new URL("https://celestrak.com/pub/satcat.csv").openConnection();
        try {
            return IOUtils.toString(connection.getInputStream(), StandardCharsets.UTF_8).lines()
                    .map(l -> l.split(","))
                    .filter(t -> t.length >= 3)
                    .collect(toMap(t -> t[1], t -> t));
        } finally {
            connection.disconnect();
        }
    }

    private List<Integer> getSatIdsFromSatIntDes(Map<String, String[]> map, CredentialProvider credentials) {
        return satIntlDes.stream().flatMap(d -> {
            try {
                Integer catalogNumber = null;
                String[] strings = map.get(d);
                final String id = strings == null ? null : strings[2];
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
        }).filter(Objects::nonNull).distinct().collect(toList());
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
