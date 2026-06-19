package com.zalexdev.stryker.custom;

import androidx.annotation.NonNull;

import java.util.ArrayList;

public class Argument {
    public String name = "";
    public boolean required = false;
    public String description = "";
    public String value = "";
    public ArrayList<String> auto = new ArrayList<>();

    public String getDescription() {
        return description;
    }

    public String getName() {
        return name;
    }

    public boolean isRequired() {
        return required;
    }

    public String getValue() {
        return value;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @NonNull
    @Override
    public String toString() {
        return "Argument{" +
                "name='" + name + '\'' +
                ", required=" + required +
                ", description='" + description + '\'' +
                ", value='" + value + '\'' +
                '}';
    }

    public void setAuto(ArrayList<String> auto) {
        this.auto = auto;
    }

    public ArrayList<String> getAuto() {
        return auto;
    }
    public void addAuto(String auto) {
        this.auto.add(auto);
    }
}
