package com.example.bootserver.inventory.internal.interfaces.web;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** 仅接受 JSON 整数，避免 Jackson 把小数或字符串隐式转成库存总量。 */
public class StrictStockTotalDeserializer extends ValueDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            return context.reportInputMismatch(Long.class, "库存总量必须是 JSON 整数");
        }
        return parser.getLongValue();
    }
}
