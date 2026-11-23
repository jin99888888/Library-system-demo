package com.example.library.domain;

/** 读者性别，选填。 */
public enum Gender {

    MALE("男"),
    FEMALE("女");

    private final String label;

    Gender(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
