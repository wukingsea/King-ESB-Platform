package com.king.esb.transform;

import com.king.esb.core.route.TransformType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the JSON↔XML transformers, one bean per direction. */
@Configuration
public class EsbTransformConfiguration {

    @Bean
    public JsonXmlTransformer jsonToXmlTransformer() {
        return new JsonXmlTransformer(TransformType.JSON_TO_XML);
    }

    @Bean
    public JsonXmlTransformer xmlToJsonTransformer() {
        return new JsonXmlTransformer(TransformType.XML_TO_JSON);
    }
}
