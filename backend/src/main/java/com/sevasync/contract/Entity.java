package com.sevasync.contract;

import java.io.Serializable;
import java.util.Map;

/**
 * Contract every persistent domain object fulfils. Gives the generic storage layer
 * (CSV) and the API layer (JSON) a uniform view of an object.
 */
public interface Entity extends Serializable {

    String getId();

    /** One CSV row, in the same column order as the owning repository's header. */
    String[] toCsvRow();

    /** Flat map used by the JSON API. */
    Map<String, Object> toMap();
}
