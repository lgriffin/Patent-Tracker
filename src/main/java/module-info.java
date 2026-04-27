module com.patenttracker {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires java.sql;
    requires java.net.http;
    requires java.desktop;
    requires jdk.jsobject;
    requires com.opencsv;
    requires com.fasterxml.jackson.databind;
    requires org.xerial.sqlitejdbc;
    requires org.apache.pdfbox;

    opens com.patenttracker to javafx.fxml;
    opens com.patenttracker.controller to javafx.fxml;
    opens com.patenttracker.model to javafx.fxml, com.fasterxml.jackson.databind;
    opens com.patenttracker.service to com.fasterxml.jackson.databind;

    exports com.patenttracker;
    exports com.patenttracker.model;
    exports com.patenttracker.dao;
    exports com.patenttracker.service;
    exports com.patenttracker.controller;
    exports com.patenttracker.util;
}
