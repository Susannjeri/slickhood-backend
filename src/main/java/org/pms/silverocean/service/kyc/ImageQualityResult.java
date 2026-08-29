package org.pms.silverocean.service.kyc;

public record ImageQualityResult(boolean accepted, int width, int height, double sharpness, String reason) { }
