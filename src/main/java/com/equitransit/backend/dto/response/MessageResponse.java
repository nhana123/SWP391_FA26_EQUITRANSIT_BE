/*
 * MessageResponse
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Return safe response data.
 */
package com.equitransit.backend.dto.response;





/** Contains safe API response data. */
public class MessageResponse {
    private String message;

    /**
     * Returns message.
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets message.
     * @param message the value to store
     */
    public void setMessage(String message) {
        this.message = message;
    }


}
