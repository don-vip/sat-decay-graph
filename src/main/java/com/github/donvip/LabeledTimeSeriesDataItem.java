package com.github.donvip;

import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.TimeSeriesDataItem;

class LabeledTimeSeriesDataItem extends TimeSeriesDataItem {
    private static final long serialVersionUID = 1L;

    private final String label;

    /**
     * Constructs a new data item that associates a value with a time period.
     *
     * @param period the time period ({@code null} not permitted).
     * @param value the value associated with the time period.
     */
    public LabeledTimeSeriesDataItem(RegularTimePeriod period, double value, String label) {
        super(period, value);
        this.label = label;
    }

    /**
     * Constructs a new data item that associates a value with a time period.
     *
     * @param period the time period ({@code null} not permitted).
     * @param value the value ({@code null} permitted).
     */
    public LabeledTimeSeriesDataItem(RegularTimePeriod period, Number value, String label) {
        super(period, value);
        this.label = label;
    }

    String getLabel() {
        return label;
    }
}