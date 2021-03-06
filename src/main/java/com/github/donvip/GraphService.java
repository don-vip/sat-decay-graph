package com.github.donvip;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.HeadlessException;
import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.AbstractXYItemRenderer;
import org.jfree.chart.renderer.xy.SamplingXYLineRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.time.TimeSeriesDataItem;
import org.jfree.svg.SVGGraphics2D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.github.donvip.GpHistoryQuery.GpHistory;

@Service
public class GraphService {

    private static final Logger logger = LoggerFactory.getLogger(GraphService.class);

    @Value("${plotMode:distinct}")
    private PlotMode plotMode;

    @Value("${combinedFileName:output.svg}")
    private String combinedFileName;

    @Value("${domainGridlinesVisible:false}")
    private boolean domainGridlinesVisible;

    @Value("${rangeGridlinesVisible:false}")
    private boolean rangeGridlinesVisible;

    @Value("${showApoapsis:true}")
    private boolean showApoapsis;

    @Value("${showPeriapsis:true}")
    private boolean showPeriapsis;

    @Value("${showLegend:true}")
    private boolean showLegend;

    @Value("${useNameInLegend:true}")
    private boolean useNameInLegend;

    @Value("${startDate:#{null}}")
    private Instant startDate;

    @Value("${endDate:#{null}}")
    private Instant endDate;

    @Value("${dateFormat:#{null}}")
    private String dateFormat;

    @Value("${minAltitude:0.0}")
    private double minAltitude;

    @Value("${satIntlDes:#{T(java.util.Collections).emptyList()}}")
    private List<String> satIntlDes;

    @Value("${satIdsExcl:#{T(java.util.Collections).emptyList()}}")
    private List<Integer> satIdsExcl;

    @Value("${width:1920}")
    private int width;

    @Value("${height:1080}")
    private int height;

    @Value("${strokeWidth:4.0}")
    private float strokeWidth;

    @Value("${shapeSize:10.0}")
    private double shapeSize;

    @Value("${autoPopulateSeriesPaint:true}")
    private boolean autoPopulateSeriesPaint;

    @Value("${openFile:false}")
    private boolean openFile;

    @Value("${debug:false}")
    private boolean debug;

    @Value("#{${overrides:T(java.util.Collections).emptyMap()}}")
    private Map<Integer, Integer> overrides;

    @Value("${customRendererClass:#{null}}")
    private Class<? extends AbstractXYItemRenderer> customRendererClass;

    @Autowired
    private CelestrakService celestrak;

    @Autowired
    private SpaceTrackService spaceTrack;

    private static String generateSVGForChart(JFreeChart chart, int width, int height) {
        SVGGraphics2D g2 = new SVGGraphics2D(width, height);
        chart.draw(g2, new Rectangle(width, height));
        return g2.getSVGElement(chart.getID());
    }

    private void addTimeSeries(TimeSeriesCollection apoApsisCollection, TimeSeriesCollection periApsisCollection,
            List<GpHistory> history, String prefix, Integer objectId, boolean distinguish,
            List<GpHistory> overridenHistories) {
        final TimeSeries apoapsis = new IdentifiedTimeSeries(objectId, prefix + (distinguish ? "Apoapsis" : ""));
        final TimeSeries periapsis = new IdentifiedTimeSeries(objectId, prefix + (distinguish ? "Periapsis" : ""));

        for (GpHistory gp : history) {
            Integer overridenObjectId = overrides.get(gp.getGpId());
            if (overridenObjectId == null || overridenObjectId.equals(objectId)) {
                addApoapsisAndPeriapsis(apoapsis, periapsis, gp);
            } else {
                overridenHistories.add(gp);
            }
        }

        apoApsisCollection.addSeries(apoapsis);
        periApsisCollection.addSeries(periapsis);
    }

    private void addApoapsisAndPeriapsis(final TimeSeries apoapsis, final TimeSeries periapsis, GpHistory gp) {
        String label = gp.getGpId().toString();
        RegularTimePeriod date = new Millisecond(Date.from(gp.getEpoch().toInstant()));
        if (showApoapsis) {
            apoapsis.addOrUpdate(new LabeledTimeSeriesDataItem(date, gp.getApoapsis(), label));
        }
        if (showPeriapsis) {
            periapsis.addOrUpdate(new LabeledTimeSeriesDataItem(date, gp.getPeriapsis(), label));
        }
    }

    /**
     * Creates one or two datasets, based on the {@code distringuish} parameter
     *
     * @param histories Map of GP_HISTORY records per object id, as returned by
     *            Space-Track API
     * @param names Map of object names per object id
     * @param distinguish if {@code true}, creates a single data set where apoapsis
     *            and periapsis series have distinguished names. If {@code false},
     *            creates two datasets where apoapsis and periapsis have the same
     *            name. The use of two datasets allow to render the plot with the
     *            same colors for both series.
     * @return the created datasets
     */
    private List<TimeSeriesCollection> createDatasets(Map<Integer, List<GpHistory>> histories,
            Map<Integer, String> names, boolean distinguish) {
        TimeSeriesCollection apoapsis = new TimeSeriesCollection();
        TimeSeriesCollection periapsis = new TimeSeriesCollection();
        List<GpHistory> overridenHistories = new ArrayList<>();
        histories.forEach((id, history) -> {
            String name = useNameInLegend ? names.get(id) : id.toString();
            addTimeSeries(apoapsis, distinguish ? apoapsis : periapsis, history, name.isEmpty() ? name : name + ' ', id,
                    distinguish, overridenHistories);
        });
        overridenHistories.forEach(gp -> {
            Integer id = overrides.get(gp.getGpId());
            List<IdentifiedTimeSeries> apoapsises = findSeries(apoapsis, id);
            addApoapsisAndPeriapsis(apoapsises.get(0),
                    distinguish ? apoapsises.get(1) : findSeries(periapsis, id).get(0), gp);
        });
        return distinguish ? List.of(apoapsis) : List.of(apoapsis, periapsis);
    }

    @SuppressWarnings("unchecked")
    private static List<IdentifiedTimeSeries> findSeries(TimeSeriesCollection collection, Integer objectId) {
        return (List<IdentifiedTimeSeries>) collection.getSeries().stream()
                .filter(s -> ((IdentifiedTimeSeries) s).getObjectId().equals(objectId)).collect(toList());
    }

    private JFreeChart createChart(List<TimeSeriesCollection> datasets, String title)
            throws SecurityException, ReflectiveOperationException {
        // Create plot (downsampling very large data to avoid huge SVG files)
        XYPlot plot = createPlot(datasets);

        // Create and return chart
        JFreeChart chart = new JFreeChart(title, JFreeChart.DEFAULT_TITLE_FONT, plot, showLegend);
        chart.setBackgroundPaint(Color.WHITE);
        return chart;
    }

    private XYPlot createPlot(List<TimeSeriesCollection> datasets)
            throws SecurityException, ReflectiveOperationException {
        // Time axis, UTC / English
        DateAxis timeAxis = new DateAxis("Time (UTC)", TimeZone.getTimeZone("UTC"), Locale.ENGLISH);
        timeAxis.setLowerMargin(0.02);
        timeAxis.setUpperMargin(0.02);
        if (dateFormat != null && !dateFormat.trim().isEmpty()) {
            timeAxis.setDateFormatOverride(new SimpleDateFormat(dateFormat.trim(), Locale.ENGLISH));
        }

        // Value axis (same on both sides for readability)
        NumberAxis leftAxis = new NumberAxis("Kilometers");
        leftAxis.setAutoRangeIncludesZero(false);
        NumberAxis rightAxis = new NumberAxis(null);
        rightAxis.setAutoRangeIncludesZero(false);

        // Create plot (downsampling very large data to avoid huge SVG files)
        double delta = shapeSize / 2.0;
        @SuppressWarnings("unchecked")
        boolean small = datasets.get(0).getSeries().stream().mapToInt(s -> ((TimeSeries) s).getItemCount()).sum() < 250;
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

    private AbstractXYItemRenderer createRenderer(boolean small, double delta)
            throws ReflectiveOperationException, SecurityException {
        AbstractXYItemRenderer renderer = customRendererClass != null
                ? customRendererClass.getConstructor().newInstance()
                : small ? new XYLineAndShapeRenderer(true, true)
                : new SatSamplingXYLineRenderer();
        renderer.setAutoPopulateSeriesPaint(autoPopulateSeriesPaint);
        renderer.setDefaultStroke(new BasicStroke(strokeWidth));
        renderer.setDefaultShape(new Ellipse2D.Double(-delta, -delta, shapeSize, shapeSize));
        renderer.setDataBoundsIncludesVisibleSeriesOnly(false);
        renderer.setAutoPopulateSeriesStroke(false);
        renderer.setAutoPopulateSeriesShape(false);
        renderer.setDefaultItemLabelsVisible(debug);
        renderer.setDefaultItemLabelGenerator((dataset, series, item) -> {
            if (dataset instanceof TimeSeriesCollection) {
                TimeSeriesDataItem dataItem = ((TimeSeriesCollection) dataset).getSeries(series).getDataItem(item);
                if (dataItem instanceof LabeledTimeSeriesDataItem) {
                    return ((LabeledTimeSeriesDataItem) dataItem).getLabel();
                }
            }
            return null;
        });
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

    private void doGenerateGraphs(List<Integer> ids, Map<String, String[]> map)
            throws IOException, InterruptedException, SecurityException, ReflectiveOperationException {
        Map<Integer, String> names = new TreeMap<>();
        Map<Integer, List<GpHistory>> histories = new TreeMap<>();
        for (Integer id : ids) {
            logger.info("Fetching history for satellite {}", id);
            List<GpHistory> history = spaceTrack.fetchHistory(id, startDate, endDate, minAltitude);
            if (history.isEmpty()) {
                logger.error("Unable to generate graph for satellite {} (empty history)", id);
            } else {
                String objectName = findObjectName(map, id, history);
                logger.info("Found {} gp_history records for satellite {} - {}", history.size(), id, objectName);
                names.put(id, objectName);
                switch (plotMode) {
                case combined:
                    histories.put(id, history);
                    break;
                case distinct:
                    logger.info("Generating graph for satellite {} - {}", id, objectName);
                    String filename = objectName.replace('/', '-').replace('\\', '-') + " altitude.svg";
                    Files.writeString(Path.of(filename), generateSVGForChart(
                            createChart(createDatasets(Map.of(id, history), Map.of(id, objectName), true),
                                    objectName + " altitude"),
                            width, height));
                    logger.info("Graph generated for satellite {}: {}", id, filename);
                    openGraph(filename);
                    break;
                default:
                    throw new UnsupportedOperationException(Objects.toString(plotMode));
                }
            }
        }
        if (PlotMode.combined == plotMode && !histories.isEmpty()) {
            Set<String> objectNames = new TreeSet<>(names.values().stream().collect(toSet()));
            logger.info("Generating graph for satellites {} - {}", ids, objectNames);
            Files.writeString(Path.of(combinedFileName),
                    generateSVGForChart(
                            createChart(createDatasets(histories, names, false),
                                    (showApoapsis && showPeriapsis ? "Altitude of "
                                            : showApoapsis ? "Apoapsis of " : "Periapsis of ")
                                            + String.join(", ", objectNames)),
                            width, height));
            logger.info("Graph generated for satellites {}: {}", ids, combinedFileName);
            openGraph(combinedFileName);
        }
    }

    private void openGraph(String filename) throws IOException {
        if (openFile) {
            try {
                Desktop.getDesktop().browse(Path.of(filename).toUri());
            } catch (HeadlessException e) {
                String os = System.getProperty("os.name");
                if (os != null && os.toLowerCase(Locale.ENGLISH).startsWith("windows")) {
                    Runtime.getRuntime().exec(String.format("cmd /c start %s", filename));
                } else {
                    Runtime.getRuntime().exec(String.format("open %s", filename));
                }
            }
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

    private List<Integer> getSatIdsFromSatIntDes(Map<String, String[]> map) {
        return satIntlDes.stream().flatMap(d -> {
            try {
                return d.endsWith("*") ? mapMultiId(d, map) : mapSingleId(d, map);
            } catch (IOException | InterruptedException | NumberFormatException e) {
                logger.error("Failed to retrieve satcat " + d, e);
                return Stream.empty();
            }
        }).filter(Objects::nonNull).distinct().collect(toList());
    }

    private Stream<Integer> mapSingleId(String d, Map<String, String[]> map)
            throws JsonParseException, JsonMappingException, IOException, InterruptedException {
        Integer catalogNumber = null;
        String[] strings = map.get(d.trim());
        final String id = strings == null ? null : strings[2];
        if (id != null && !id.isBlank()) {
            catalogNumber = Integer.valueOf(id);
        } else {
            catalogNumber = spaceTrack.findCatalogNumber(d.trim());
        }
        if (catalogNumber != null) {
            logger.info("Mapped satellite international designator {} to catalog number {}", d, catalogNumber);
            return Stream.of(catalogNumber);
        }
        return Stream.empty();
    }

    private Stream<Integer> mapMultiId(String d, Map<String, String[]> map)
            throws InterruptedException, JsonParseException, JsonMappingException, IOException {
        String des = d.substring(0, d.lastIndexOf('*')).trim();

        List<Integer> catalogNumbers = map.entrySet().stream()
                .filter(e -> e.getKey().startsWith(des)).map(e -> {
            String[] strings = e.getValue();
            String id = strings == null ? null : strings[2];
            return id != null && !id.isBlank() ? Integer.valueOf(id) : null;
        }).filter(Objects::nonNull).sorted().collect(toList());

        if (catalogNumbers.isEmpty()) {
            catalogNumbers = spaceTrack.findCatalogNumbers(des);
        }

        catalogNumbers.removeAll(satIdsExcl);
        if (!catalogNumbers.isEmpty()) {
            logger.info("Mapped satellite international designator pattern {} to catalog numbers {}", d, catalogNumbers);
            return catalogNumbers.stream();
        }
        return Stream.empty();
    }

    public void generateGraphs()
            throws IOException, InterruptedException, SecurityException, ReflectiveOperationException {
        logger.info("Generating graphs for {}", satIntlDes);
        if (!satIntlDes.isEmpty()) {
            // SpaceTrack API has a very restrictive API Throttling, so download a mapping
            // from CelesTrak first
            Map<String, String[]> map = celestrak.getCelestrakMapping();
            doGenerateGraphs(getSatIdsFromSatIntDes(map), map);
        }
    }
}
