package pl.miniplanes.model;

/** A destination symbol; transfers never create or duplicate a passenger. */
public record Passenger(long id, Shape destination) {}
