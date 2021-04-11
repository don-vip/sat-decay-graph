package com.github.donvip;

import java.awt.Rectangle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYDataset;
import org.jfree.svg.SVGGraphics2D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.github.donvip.GpHistoryQuery.GpHistory;
import com.github.donvip.GpHistoryQuery.GpHistoryQueryField;
import com.stevenpaligo.spacetrack.client.credential.DefaultCredentialProvider;
import com.stevenpaligo.spacetrack.client.predicate.Equal;

@Service
public class SatDecayGraphService {

    private Logger logger = LoggerFactory.getLogger(SatDecayGraphService.class);
    
    private static String generateSVGForChart(JFreeChart chart, int width, int height, String defsPrefix) {
        SVGGraphics2D g2 = new SVGGraphics2D(width, height);
        g2.setDefsKeyPrefix(defsPrefix);
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

     private static JFreeChart createChart(XYDataset dataset, String title) {
        return ChartFactory.createTimeSeriesChart(
                title, "Time", "Kilometers", dataset, true, false, false);
     }

    @Autowired
    public SatDecayGraphService(@Value("${spaceTrackLogin}") String spaceTrackLogin, @Value("${spaceTrackPassword}") String spaceTrackPassword, @Value("${satId}") int satId)
            throws IOException {
        logger.info("Fetching history for satellite {}", satId);
        List<GpHistory> history = new GpHistoryQuery().setCredentials(new DefaultCredentialProvider(spaceTrackLogin, spaceTrackPassword))
                        .addPredicate(new Equal<>(GpHistoryQueryField.CATALOG_NUMBER, satId)).execute()
                        .stream().sorted(Comparator.comparing(GpHistory::getEpoch)).collect(Collectors.toList());
        logger.info("Generating graph for satellite {}", satId);
        final XYDataset dataset = createDataset(history);         
        final JFreeChart chart = createChart(dataset, history.get(0).getObjectName());
        Files.writeString(Path.of("output.svg"), generateSVGForChart(chart, 800, 600, "foo"));
        logger.info("Graph generated for satellite {}", satId);
    }
}
