/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.tools;

import com.devexperts.io.ClassUtil;
import com.devexperts.io.Marshalled;
import com.devexperts.logging.Logging;
import com.devexperts.qd.QDLog;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIException;
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.message.RMIRequestType;
import com.devexperts.rmi.message.RMIRoute;
import com.devexperts.rmi.task.RMIServiceDescriptor;
import com.devexperts.rmi.task.RMIServiceId;
import com.devexperts.services.ServiceProvider;
import com.devexperts.util.ConfigUtil;
import com.devexperts.util.LogUtil;
import com.dxfeed.promise.Promise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * RMI Invocation tool.
 */
@ToolSummary(
    info = "Invokes RMI method.",
    argString = {
        "<address> <service> <method> [<arg> ...]"
    },
    arguments = {
        "<address> -- address to connect for invocation (see @link{address})",
        "<target>  -- service name to invoke, use <name>@<id> for targeted invocation",
        "<method>  -- <method-name>[:<result-type>], void by default",
        "<arg>     -- arguments in <value>[:<type>] format, auto-detect type by default"
    }
)
@ServiceProvider
public class Invoke extends AbstractTool {
    private final OptionLog logfile = OptionLog.getInstance();

    @Override
    protected Option[] getOptions() {
        return new Option[] { logfile };
    }

    @Override
    protected void executeImpl(String[] args) {
        if (args.length == 0) {
            noArguments();
        }
        if (args.length < 3) {
            wrongNumberOfArguments();
        }

        String address = args[0];
        String targetArg = args[1];
        int serviceIdSeparatorPos = targetArg.indexOf('@');
        boolean targeted = serviceIdSeparatorPos != -1;
        String serviceName = targeted ? targetArg.substring(0, serviceIdSeparatorPos) : targetArg;
        Class<?> returnType = stripType(args, 2, void.class);
        String method = args[2];
        List<Class<?>> types = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        for (int i = 3; i < args.length; i++) {
            Class<?> type = stripType(args, i, null);
            if (type == null)
                type = autoDetectType(args[i]);
            types.add(type);
            parameters.add(ConfigUtil.convertStringToObject(type, args[i]));
        }

        RMIOperation<?> operation = RMIOperation.valueOf(serviceName, returnType, method,
            types.toArray(new Class<?>[types.size()]));
        log.info("Using address " + LogUtil.hideCredentials(address));
        log.info("Invoking " + operation + " with " + parameters);

        RMIEndpoint endpoint = RMIEndpoint.createEndpoint(RMIEndpoint.Side.CLIENT);
        Promise<RMIServiceId> targetPromise = new Promise<>();
        if (targeted) {
            endpoint.getClient().getService(serviceName).addServiceDescriptorsListener(descriptors ->
                descriptors.stream()
                    .map(RMIServiceDescriptor::getServiceId)
                    .filter(id -> id.toString().equals(targetArg))
                    .forEach(targetPromise::complete));
        } else {
            targetPromise.complete(null);
        }
        endpoint.connect(address);

        if (!targetPromise.awaitWithoutException(endpoint.getClient().getRequestSendingTimeout(), TimeUnit.MILLISECONDS)) {
            log.error("Service " + targetArg + " not found");
            return;
        }
        RMIRequest<?> request = createRequest(endpoint, operation, parameters, targetPromise.getResult());
        request.send();
        try {
            Object result = request.getBlocking();
            log.info("Invocation completed successfully: " + format(result));
        } catch (RMIException e) {
            log.error("Invocation completed with exception", e);
        }
    }

    private <T> RMIRequest<T> createRequest(RMIEndpoint endpoint, RMIOperation<T> operation, List<Object> parameters,
        RMIServiceId target)
    {
        Marshalled<Object[]> marshalledParams = Marshalled.forObject(parameters.toArray(),
            operation.getParametersMarshaller());
        return endpoint.getClient().createRequest(new RMIRequestMessage<>(
            RMIRequestType.DEFAULT, operation, marshalledParams, RMIRoute.EMPTY, target));
    }

    private String format(Object result) {
        if (result instanceof Object[])
            return Arrays.deepToString((Object[]) result);
        else
            return Objects.toString(result);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private Class<?> autoDetectType(String s) {
        try {
            Integer.parseInt(s);
            return int.class;
        } catch (NumberFormatException ignored) {}
        try {
            Long.parseLong(s.endsWith("l") || s.endsWith("L") ? s.substring(0, s.length() - 1) : s);
            return long.class;
        } catch (NumberFormatException ignored) {}
        if (s.endsWith("f") || s.endsWith("F"))
            try {
                Float.parseFloat(s);
                return float.class;
            } catch (NumberFormatException ignored) {}
        try {
            Double.parseDouble(s);
            return double.class;
        } catch (NumberFormatException ignored) {}
        return String.class;
    }

    private Class<?> stripType(String[] args, int index, Class<?> type) {
        int pos = args[index].lastIndexOf(':');
        if (pos < 0)
            return type;
        String name = args[index].substring(pos + 1);
        args[index] = args[index].substring(0, pos);
        try {
            return ClassUtil.getTypeClass(name, null);
        } catch (ClassNotFoundException e) {
            throw new OptionParseException("Type " + name + " is not found", e);
        }
    }

    public static void main(String[] args) {
        Tools.executeSingleTool(Invoke.class, args);
    }
}
