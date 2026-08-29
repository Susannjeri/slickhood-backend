package org.pms.silverocean.service.users;

public record ProfileCompleteness(boolean name, boolean identificationNumber, boolean phoneNumber, boolean taxPin) {
}
