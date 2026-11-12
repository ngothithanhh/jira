package com.example.jira.dto.admin;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;

/** Không để Jackson ép 1.5 hoặc chuỗi "1" thành ID1 trên API quyền; body sai dừng400 trước service. */
public class PermissionIdDeserializer extends JsonDeserializer<Integer> {
    @Override public Integer deserialize(JsonParser parser,DeserializationContext context)throws IOException{
        if(parser.currentToken()!=JsonToken.VALUE_NUMBER_INT)return (Integer)context.handleUnexpectedToken(Integer.class,parser);
        return parser.getIntValue();
    }
}
