package org.pms.silverocean.service.visitor.wrappers;

/** The access code is returned only once. Clients should render it as QR or text. */
public record RegisteredVisitDTO(VisitorDTO visit, String accessCode) {}
