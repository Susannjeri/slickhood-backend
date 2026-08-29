package org.pms.silverocean.controller.wrappers;

public record LeaseMessageRequest(String message, long leaseId) {
}
