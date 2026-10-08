package com.frontendbase.api.common.validation;
import java.io.IOException;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
/** Only integer JSON tokens are accepted; no floating point or string coercion. */
public class StrictLongDeserializer extends JsonDeserializer<Long> {
    @Override public Long deserialize(JsonParser p, DeserializationContext c) throws IOException {
        if (!p.hasToken(JsonToken.VALUE_NUMBER_INT)) throw JsonMappingException.from(p, "Value must be an integer");
        return p.getLongValue();
    }
}
