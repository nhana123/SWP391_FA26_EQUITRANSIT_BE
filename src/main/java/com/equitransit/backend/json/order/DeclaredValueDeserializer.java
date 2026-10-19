/*
 * DeclaredValueDeserializer
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Bound declared-value input before expanding decimal notation.
 */
package com.equitransit.backend.json.order;


import java.io.IOException;
import java.math.BigDecimal;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;


/** Accepts plain decimals within SQL DECIMAL(18,2), never exponent input. */
public class DeclaredValueDeserializer extends JsonDeserializer<BigDecimal> {

    /**
     * Checks bounded plain notation before constructing a decimal.
     * @param parser JSON input
     * @param context configured deserialization context
     * @return exact declared value; positivity is validated by the DTO
     * @throws IOException if the input is unsupported
     */
    @Override
    public BigDecimal deserialize(JsonParser parser,
            DeserializationContext context) throws IOException {
        JsonToken token = parser.currentToken();
        String value;

        if (token != JsonToken.VALUE_STRING
                && token != JsonToken.VALUE_NUMBER_INT
                && token != JsonToken.VALUE_NUMBER_FLOAT) {
            throw invalid(parser);
        }
        value = parser.getText();
        if (value.length() > 19
                || !value.matches("[0-9]{1,16}(\\.[0-9]{1,2})?")) {
            throw invalid(parser);
        }
        return new BigDecimal(value);
    }

    private JsonMappingException invalid(JsonParser parser) {
        return JsonMappingException.from(parser,
                "Declared value requires a bounded plain decimal.");
    }
}
