package com.arumdaun.church;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Client implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int clientId;
    private String koreanName;
    private String englishSurname;
    private String englishGivenName;
    private String englishMiddleName;
    private String phone1;
    private String phone2;
    private String streetAddress;
    private String city;
    private String state;
    private String zipCode;
    private boolean deleted;
    private LocalDate deletedDate;
    private final List<Purchase> purchases = new ArrayList<>();

    public Client(int clientId, String koreanName, String englishSurname, String englishGivenName,
            String englishMiddleName, String phone1, String phone2, String streetAddress, String city,
            String state, String zipCode) {
        this.clientId = clientId;
        this.koreanName = koreanName;
        this.englishSurname = englishSurname;
        this.englishGivenName = englishGivenName;
        this.englishMiddleName = englishMiddleName;
        this.phone1 = phone1;
        this.phone2 = phone2;
        this.streetAddress = streetAddress == null ? "" : streetAddress;
        this.city = city == null ? "" : city;
        this.state = state == null ? "" : state;
        this.zipCode = zipCode == null ? "" : zipCode;
        this.deleted = false;
        this.deletedDate = null;
    }

    public int getClientId() {
        return clientId;
    }

    public String getKoreanName() {
        return koreanName;
    }

    public String getDisplayName() {
        return koreanName + " / " + getEnglishFullName();
    }

    public String getEnglishFullName() {
        String middle = englishMiddleName == null || englishMiddleName.isEmpty()
                ? ""
                : " " + englishMiddleName;
        return englishSurname + ", " + englishGivenName + middle;
    }

    public String getPhone1() {
        return phone1;
    }

    public String getPhone2() {
        return phone2;
    }

    public String getEnglishSurname() {
        return englishSurname;
    }

    public String getEnglishGivenName() {
        return englishGivenName;
    }

    public String getEnglishMiddleName() {
        return englishMiddleName;
    }

    public String getStreetAddress() {
        return streetAddress;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getZipCode() {
        return zipCode;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public LocalDate getDeletedDate() {
        return deletedDate;
    }

    public void softDelete() {
        deleted = true;
        deletedDate = LocalDate.now();
    }

    public void updateDetails(String koreanName, String englishSurname, String englishGivenName,
            String englishMiddleName, String phone1, String phone2, String streetAddress,
            String city, String state, String zipCode) {
        this.koreanName = koreanName;
        this.englishSurname = englishSurname;
        this.englishGivenName = englishGivenName;
        this.englishMiddleName = englishMiddleName;
        this.phone1 = phone1;
        this.phone2 = phone2;
        this.streetAddress = streetAddress;
        this.city = city;
        this.state = state;
        this.zipCode = zipCode;
    }

    public void addPurchase(Purchase purchase) {
        purchases.add(purchase);
    }

    @Override
    public String toString() {
        return "Client Id: " + clientId + " | Client Name: " + getDisplayName() +
                " | Phones: " + phone1 + ", " + (phone2 == null ? "" : phone2);
    }

}