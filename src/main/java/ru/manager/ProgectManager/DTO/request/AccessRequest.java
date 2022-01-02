package ru.manager.ProgectManager.DTO.request;

import lombok.Getter;

@Getter
public class AccessRequest {
    private long projectId;
    private boolean hasAdmin;
    private boolean disposable;
    private int liveTimeInDays;
}
