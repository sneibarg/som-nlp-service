package org.springy.som.nlp.judge;

/**
 * A verdict together with the request type it belongs to.
 * @param request the request type name
 * @param name the verdict name
 * @param meaning what the verdict means for that request type
 */
public record VerdictListing(String request, String name, String meaning) {
}
