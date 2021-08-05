package com.github.donvip;

import org.jfree.data.time.TimeSeries;

public class IdentifiedTimeSeries extends TimeSeries {

    private static final long serialVersionUID = 1L;

    private final Integer objectId;

    public IdentifiedTimeSeries(Integer objectId, String name, String domain, String range) {
        super(name, domain, range);
        this.objectId = objectId;
    }

    public IdentifiedTimeSeries(Integer objectId, String name) {
        super(name);
        this.objectId = objectId;
    }

    Integer getObjectId() {
        return objectId;
    }
}
