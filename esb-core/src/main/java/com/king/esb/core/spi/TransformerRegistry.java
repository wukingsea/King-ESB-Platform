package com.king.esb.core.spi;

import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import com.king.esb.core.route.TransformType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Resolves the {@link Transformer} for a given {@link TransformType}. */
public class TransformerRegistry {

    private final Map<TransformType, Transformer> transformers = new EnumMap<>(TransformType.class);

    public TransformerRegistry(List<Transformer> transformerList) {
        for (Transformer transformer : transformerList) {
            transformers.put(transformer.type(), transformer);
        }
    }

    public Transformer get(TransformType type) {
        Transformer transformer = transformers.get(type);
        if (transformer == null) {
            throw new EsbException(EsbErrorCode.TRANSFORM_ERROR,
                    "No transformer registered for type " + type);
        }
        return transformer;
    }

    public boolean supports(TransformType type) {
        return transformers.containsKey(type);
    }
}
