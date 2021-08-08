package com.github.donvip;

import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class CelestrakService {

    private static final Logger logger = LoggerFactory.getLogger(CelestrakService.class);

    @Cacheable("celestrakSatCat")
    public Map<String, String[]> getCelestrakMapping() throws IOException {
        logger.info("Retrieving SATCAT data from CelesTrak...");
        HttpsURLConnection connection = (HttpsURLConnection) new URL("https://celestrak.com/pub/satcat.csv")
                .openConnection();
        try {
            return IOUtils.toString(connection.getInputStream(), StandardCharsets.UTF_8).lines().map(l -> l.split(","))
                    .filter(t -> t.length >= 3).collect(toMap(t -> t[1], t -> t));
        } finally {
            connection.disconnect();
        }
    }
}
