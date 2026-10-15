package com.sevasync.contract;

import java.util.Map;

/**
 * Contract every persistent domain object fulfils. Gives the generic storage layer and the JSON API a uniform view
 * of an object, without either of them knowing the concrete class.
 */
public interface Entity {

    String getId();

    /** Flat, JSON-friendly view of the object (the API serialises this map). */
    Map<String, Object> toMap();
}
