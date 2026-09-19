package com.ofss.services;

public interface NotificationDispatcher {

    int dispatchDueNotifications(int maximumBatchSize);
}
