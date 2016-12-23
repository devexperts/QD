/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.rmi.impl;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.devexperts.rmi.RMIExceptionType;
import com.devexperts.rmi.message.RMICancelType;
import com.devexperts.rmi.message.RMIRequestType;
import com.devexperts.rmi.task.RMIChannelType;

/**
 * The server side of {@link RMIConnection}.
 */
class TasksManager {

	private final RMIConnection connection;
	private final RunningTask runningTasks = new RunningTask();
	private final Queue<RMITaskResponse> completedTasks = new ConcurrentLinkedQueue<>();


	TasksManager(RMIConnection connection) {
		this.connection = connection;
	}

	void registerTask(RMITaskImpl<?> taskImpl) {
		runningTasks.add(taskImpl);
		if (connection.closed) {
			taskImpl.cancel(RMIExceptionType.DISCONNECTION);
			return;
		}
	}

	RMITaskResponse pollCompletedTask() {
		return completedTasks.poll();
	}

	int completedTaskSize() {
		return completedTasks.size();
	}

	void notifyTaskCompleted(RMITaskImpl<?> taskImpl) {
		runningTasks.remove(taskImpl);
		if (taskImpl.getRequestMessage().getRequestType() == RMIRequestType.DEFAULT) {
			completedTasks.add(new RMITaskResponse(taskImpl));
			connection.messageAdapter.rmiMessageAvailable(RMIQueueType.RESPONSE);
		}
	}

	void notifyTaskCompleted(RMIRequestType type, RMITaskResponse taskImpl) {
		if (type == RMIRequestType.DEFAULT) {
			completedTasks.add(taskImpl);
			connection.messageAdapter.rmiMessageAvailable(RMIQueueType.RESPONSE);
		}
	}

	void close() {
		runningTasks.close();
	}

	//if channelId = 0 => channel cancel
	void cancelTask(long requestId, long channelId, int cancellationFlags, RMIChannelType type) {
		RMITaskImpl<?> task = runningTasks.removeById(requestId, channelId, type);
		if (task == null)
			return;
		if ((cancellationFlags & RMICancelType.ABORT_RUNNING.getId()) != 0)
			task.cancel(RMIExceptionType.CANCELLED_DURING_EXECUTION);
		else
			task.cancelWithConfirmation();
	}

	void cancelAllTasks(long channelId, int cancellationFlags, RMIChannelType type) {
		Set<RMITaskImpl<?>> tasks = runningTasks.removeAllById(channelId, type);
		if (tasks == null)
			return;
		for (RMITaskImpl<?> task : tasks) {
			if ((cancellationFlags & RMICancelType.ABORT_RUNNING.getId()) != 0)
				task.cancel(RMIExceptionType.CANCELLED_DURING_EXECUTION);
			else
				task.cancelWithConfirmation();
		}
	}
}
