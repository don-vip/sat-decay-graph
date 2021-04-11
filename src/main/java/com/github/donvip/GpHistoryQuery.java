package com.github.donvip;

import org.threeten.extra.scale.UtcInstant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.github.donvip.GpHistoryQuery.GpHistory;
import com.github.donvip.GpHistoryQuery.GpHistoryQueryField;
import com.stevenpaligo.spacetrack.client.Query;
import com.stevenpaligo.spacetrack.client.query.QueryField;
import com.stevenpaligo.spacetrack.client.util.UtcInstantDeserializer;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Class for querying ALL historical SGP4 keplerian element sets for each man-made earth-orbiting object by the 18th Space Control Squadron.
 */
public class GpHistoryQuery extends Query<GpHistoryQueryField, GpHistory, GpHistoryQuery> {

  public GpHistoryQuery() {
    super("gp_history", GpHistory.class);
  }

  /**
   * Fields referenced in "gp_history information" queries on <a href="https://www.space-track.org/">Space-Track.org</a>.
   */
  public enum GpHistoryQueryField implements QueryField {

    CATALOG_NUMBER {

      @Override
      public String getQueryFieldName() {
        return "NORAD_CAT_ID";
      }
    },

    OBJECT_NAME {

      @Override
      public String getQueryFieldName() {
        return "OBJECT_NAME";
      }
    },

    OBJECT_ID {

      @Override
      public String getQueryFieldName() {
        return "OBJECT_ID";
      }
    },

    RCS_CHARACTERIZATION {

      @Override
      public String getQueryFieldName() {
        return "RCS_SIZE";
      }
    },

    COUNTRY_CODE {

      @Override
      public String getQueryFieldName() {
        return "COUNTRY_CODE";
      }
    },

    EPOCH {

      @Override
      public String getQueryFieldName() {
        return "EPOCH";
      }
    }
  }

  /**
   * FClass representing results returned from "gp_history information" queries on <a href="https://www.space-track.org/">Space-Track.org</a>.
   */
  @Getter
  @Setter
  @NoArgsConstructor
  @JsonInclude(value = Include.NON_NULL)
  public static class GpHistory {

    @JsonProperty("CCSDS_OMM_VERS")
    private String ccsdsOmmVersion;

    @JsonProperty("COMMENT")
    private String comment;

    @JsonProperty("CREATION_DATE")
    @JsonDeserialize(using = UtcInstantDeserializer.class)
    private UtcInstant creationDate;

    @JsonProperty("ORIGINATOR")
    private String originator;

    @JsonProperty("OBJECT_NAME")
    private String objectName;

    @JsonProperty("OBJECT_ID")
    private String objectId;

    @JsonProperty("CENTER_NAME")
    private String centerName;

    @JsonProperty("REF_FRAME")
    private String refFrame;

    @JsonProperty("TIME_SYSTEM")
    private String timeSystem;

    @JsonProperty("MEAN_ELEMENT_THEORY")
    private String meanElementTheory;

    @JsonProperty("EPOCH")
    @JsonDeserialize(using = UtcInstantDeserializer.class)
    private UtcInstant epoch;
    
    @JsonProperty("MEAN_MOTION")
    private Double meanMotion;

    @JsonProperty("ECCENTRICITY")
    private Double eccentricity;

    @JsonProperty("INCLINATION")
    private Double inclination;

    @JsonProperty("RA_OF_ASC_NODE")
    private Double raOfAscendingNode;

    @JsonProperty("ARG_OF_PERICENTER")
    private Double argOfPericenter;

    @JsonProperty("MEAN_ANOMALY")
    private Double meanAnomaly;

    @JsonProperty("EPHEMERIS_TYPE")
    private Short ephemerisType;

    @JsonProperty("CLASSIFICATION_TYPE")
    private String classificationType;

    @JsonProperty("NORAD_CAT_ID")
    private Integer catalogNumber;

    @JsonProperty("ELEMENT_SET_NO")
    private Short elementSetNumber;

    @JsonProperty("REV_AT_EPOCH")
    private Integer revAtEpoch;

    @JsonProperty("BSTAR")
    private Double bstar;

    @JsonProperty("MEAN_MOTION_DOT")
    private Double meanMotionDot;

    @JsonProperty("MEAN_MOTION_DDOT")
    private Double meanMotionDdot;

    @JsonProperty("SEMIMAJOR_AXIS")
    private Double semimajorAxis;

    @JsonProperty("PERIOD")
    private Double period;

    @JsonProperty("APOAPSIS")
    private Double apoapsis;

    @JsonProperty("PERIAPSIS")
    private Double periapsis;

    @JsonProperty("OBJECT_TYPE")
    private String objectType;

    @JsonProperty("RCS_SIZE")
    private String rcsCharacterization;

    @JsonProperty("COUNTRY_CODE")
    private String countryCode;

    @JsonProperty("LAUNCH_DATE")
    private String launchDate;

    @JsonProperty("SITE")
    private String site;

    @JsonProperty("DECAY_DATE")
    private String decayDate;

    @JsonProperty("FILE")
    private Integer file;

    @JsonProperty("GP_ID")
    private Integer gpId;

    @JsonProperty("TLE_LINE0")
    private String tleLine0;

    @JsonProperty("TLE_LINE1")
    private String tleLine1;

    @JsonProperty("TLE_LINE2")
    private String tleLine2;

    @Override
    public String toString() {
        return "GpHistory [" 
                + (creationDate != null ? "creationDate=" + creationDate + ", " : "")
                + (originator != null ? "originator=" + originator + ", " : "")
                + (objectName != null ? "objectName=" + objectName + ", " : "")
                + (objectId != null ? "objectId=" + objectId + ", " : "")
                + (centerName != null ? "centerName=" + centerName + ", " : "")
                + (refFrame != null ? "refFrame=" + refFrame + ", " : "")
                + (timeSystem != null ? "timeSystem=" + timeSystem + ", " : "")
                + (meanElementTheory != null ? "meanElementTheory=" + meanElementTheory + ", " : "")
                + (epoch != null ? "epoch=" + epoch + ", " : "")
                + (meanMotion != null ? "meanMotion=" + meanMotion + ", " : "")
                + (eccentricity != null ? "eccentricity=" + eccentricity + ", " : "")
                + (inclination != null ? "inclination=" + inclination + ", " : "")
                + (raOfAscendingNode != null ? "raOfAscendingNode=" + raOfAscendingNode + ", " : "")
                + (argOfPericenter != null ? "argOfPericenter=" + argOfPericenter + ", " : "")
                + (meanAnomaly != null ? "meanAnomaly=" + meanAnomaly + ", " : "")
                + (ephemerisType != null ? "ephemerisType=" + ephemerisType + ", " : "")
                + (classificationType != null ? "classificationType=" + classificationType + ", " : "")
                + (catalogNumber != null ? "catalogNumber=" + catalogNumber + ", " : "")
                + (elementSetNumber != null ? "elementSetNumber=" + elementSetNumber + ", " : "")
                + (revAtEpoch != null ? "revAtEpoch=" + revAtEpoch + ", " : "")
                + (bstar != null ? "bstar=" + bstar + ", " : "")
                + (meanMotionDot != null ? "meanMotionDot=" + meanMotionDot + ", " : "")
                + (meanMotionDdot != null ? "meanMotionDdot=" + meanMotionDdot + ", " : "")
                + (semimajorAxis != null ? "semimajorAxis=" + semimajorAxis + ", " : "")
                + (period != null ? "period=" + period + ", " : "")
                + (apoapsis != null ? "apoapsis=" + apoapsis + ", " : "")
                + (periapsis != null ? "periapsis=" + periapsis + ", " : "")
                + (objectType != null ? "objectType=" + objectType + ", " : "")
                + (rcsCharacterization != null ? "rcsCharacterization=" + rcsCharacterization + ", " : "")
                + (countryCode != null ? "countryCode=" + countryCode + ", " : "")
                + (launchDate != null ? "launchDate=" + launchDate + ", " : "")
                + (site != null ? "site=" + site + ", " : "")
                + (decayDate != null ? "decayDate=" + decayDate + ", " : "")
                + (file != null ? "file=" + file + ", " : "") + (gpId != null ? "gpId=" + gpId + ", " : "")
                + "]";
    }
  }
}
