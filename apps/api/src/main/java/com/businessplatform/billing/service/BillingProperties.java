package com.businessplatform.billing.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Who bills and how to pay ({@code platform.billing.*}): the seller shown on invoices, the bank account for transfers,
 * the Bakong account for KHQR (empty = no QR), when invoices are due and how long a late one is tolerated.
 */
@ConfigurationProperties("platform.billing")
public record BillingProperties(String sellerName, String sellerAddress, String sellerTaxId, String bankName,
        String bankAccountName, String bankAccountNumber, String bakongAccountId, String merchantCity, Integer dueDays,
        Integer graceDays) {

    public int due() {
        return dueDays == null ? 7 : dueDays;
    }

    public int grace() {
        return graceDays == null ? 14 : graceDays;
    }

    public boolean khqr() {
        return bakongAccountId != null && !bakongAccountId.isBlank();
    }
}
