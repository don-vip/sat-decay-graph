package com.github.donvip;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
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
import com.stevenpaligo.spacetrack.client.predicate.StartsWith;

@Service
public class SpaceTrackService {

    @Value("${spaceTrackLogin}")
    private String spaceTrackLogin;

    @Value("${spaceTrackPassword}")
    private String spaceTrackPassword;

    private CredentialProvider credentials;

    @PostConstruct
    public void init() throws IOException, InterruptedException {
        credentials = new DefaultCredentialProvider(spaceTrackLogin, spaceTrackPassword);
    }

    @Cacheable("spaceTrackCatalogNumber")
    public Integer findCatalogNumber(String des)
            throws JsonParseException, JsonMappingException, IOException, InterruptedException {
        List<SatCat> satcat = new SatCatQuery().setCredentials(credentials)
                .addPredicate(new Equal<>(SatCatQueryField.INTERNATIONAL_DESIGNATOR, des)).execute();
        return apiThrottle(satcat.isEmpty() ? null : satcat.get(0).getCatalogNumber());
    }

    @Cacheable("spaceTrackCatalogNumbers")
    public List<Integer> findCatalogNumbers(String des)
            throws JsonParseException, JsonMappingException, IOException, InterruptedException {
        return apiThrottle(new SatCatQuery().setCredentials(credentials)
                .addPredicate(new StartsWith<>(SatCatQueryField.INTERNATIONAL_DESIGNATOR, des)).execute().stream()
                .map(SatCat::getCatalogNumber)
                .sorted().collect(toList()));
    }

    @Cacheable("spaceTrackGpHistory")
    public List<GpHistory> fetchHistory(Integer id, Instant startDate, Instant endDate, double minAltitude)
            throws JsonParseException, JsonMappingException, IOException, InterruptedException {
        GpHistoryQuery q = new GpHistoryQuery().setCredentials(credentials)
                .addPredicate(new Equal<>(GpHistoryQueryField.CATALOG_NUMBER, id));
        if (startDate != null) {
            q.addPredicate(new GreaterThan<>(GpHistoryQueryField.EPOCH, startDate));
        }
        if (endDate != null) {
            q.addPredicate(new LessThan<>(GpHistoryQueryField.EPOCH, endDate));
        }
        if (minAltitude < 0.0 || minAltitude > 0.0) {
            q.addPredicate(new GreaterThan<>(GpHistoryQueryField.PERIAPSIS, minAltitude));
        }
        return apiThrottle(q.execute().stream().sorted(comparing(GpHistory::getEpoch)).collect(toList()));
    }

    private static <T> T apiThrottle(T result) throws InterruptedException {
        // API throttle: Limit API queries to less than 30 requests per minute / 300
        // requests per hour
        Thread.sleep(12_000);
        return result;
    }
}
