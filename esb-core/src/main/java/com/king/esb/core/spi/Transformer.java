package com.king.esb.core.spi;

import com.king.esb.common.EsbException;
import com.king.esb.core.expression.ExpressionContext;
import com.king.esb.core.model.EsbMessage;
import com.king.esb.core.route.TransformType;

import java.util.Map;

/** Message transformation SPI implemented by {@code esb-transform} transformers. */
public interface Transformer {

    TransformType type();

    EsbMessage transform(EsbMessage input, Map<String, Object> config, ExpressionContext ctx) throws EsbException;
}
