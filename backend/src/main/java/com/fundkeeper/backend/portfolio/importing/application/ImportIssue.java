package com.fundkeeper.backend.portfolio.importing.application;

import com.fundkeeper.backend.portfolio.importing.domain.ImportIssueSeverity;

public record ImportIssue(
        Integer row,
        String field,
        String code,
        String message,
        ImportIssueSeverity severity) {

    public static ImportIssue error(
            Integer row,
            String field,
            String code,
            String message) {
        return new ImportIssue(
                row,
                field,
                code,
                message,
                ImportIssueSeverity.ERROR);
    }

    public static ImportIssue warning(
            Integer row,
            String field,
            String code,
            String message) {
        return new ImportIssue(
                row,
                field,
                code,
                message,
                ImportIssueSeverity.WARNING);
    }
}
