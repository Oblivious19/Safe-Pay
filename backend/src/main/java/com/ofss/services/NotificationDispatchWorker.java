package com.ofss.services;

public interface NotificationDispatchWorker {

    NotificationDispatchOutcome dispatch(Long notificationId);
}
