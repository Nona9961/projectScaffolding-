package com.nona.domain.rootPackage.entity;

import lombok.RequiredArgsConstructor;

/**
 * @author nona9961
 * @since 2025/3/19
 */
@RequiredArgsConstructor
public class DomainRoot {
    private final Long id;
    private SomeStatus someStatus;

    public void configureSomeStatus(SomeStatus someStatus) {
        this.someStatus = someStatus;
    }


}
