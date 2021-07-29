package com.epam.pipeline.manager;

/**
 * Convert manager performs conversion of a source object to destination type.
 *
 * @param <S> source object type.
 * @param <R> conversion request object type.
 * @param <T> target object type.
 */
public interface ConvertManager<S, R, T> {

    /**
     * Converts a source to a target according to a request.
     *
     * @param source to be converted to target.
     * @param request of a conversion to be performed.
     * @return source converted to target.
     */
    T convert(S source, R request);
}
